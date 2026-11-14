-- Run with the official Perfetto trace_processor:
-- trace_processor query -f scripts/perf/analyze_phone_trace.sql phone.pftrace
-- SurfaceFlinger end timestamps measure presentation. App slice durations do not.

CREATE PERFETTO TABLE bc_process AS
SELECT DISTINCT p.upid, p.pid, p.name
FROM process p
WHERE p.name GLOB 'com.music.bitchord*'
   OR p.upid IN (
     SELECT upid FROM actual_frame_timeline_slice
     WHERE layer_name LIKE '%com.music.bitchord/%'
   );

CREATE PERFETTO TABLE bc_frame AS
SELECT f.*, e.ts AS expected_ts, e.dur AS budget_ns,
       CASE WHEN f.present_type != 'Dropped Frame'
         THEN sf.ts + sf.dur END AS present_ts
FROM actual_frame_timeline_slice f
JOIN bc_process p USING (upid)
LEFT JOIN expected_frame_timeline_slice e
  ON e.upid = f.upid AND e.surface_frame_token = f.surface_frame_token
 AND e.layer_name = f.layer_name
LEFT JOIN actual_frame_timeline_slice sf
  ON sf.display_frame_token = f.display_frame_token
 AND sf.surface_frame_token IS NULL AND sf.dur >= 0
 AND sf.present_type != 'Dropped Frame'
WHERE f.dur >= 0;

CREATE PERFETTO TABLE bc_thread_slice AS
SELECT s.*, t.tid, t.utid, t.name AS thread_name, p.pid,
       CASE
         WHEN s.name GLOB 'Choreographer#doFrame *' THEN 'Choreographer#doFrame'
         WHEN s.name GLOB 'DrawFrames *' THEN 'DrawFrames'
         ELSE s.name
       END AS phase
FROM slice s
JOIN thread_track tr ON tr.id = s.track_id
JOIN thread t USING (utid)
JOIN bc_process p USING (upid)
WHERE s.dur >= 0;

SELECT 'coverage' AS section,
       ROUND((end_ts - start_ts) / 1e9, 3) AS retained_seconds,
       ROUND((start_ts - (SELECT int_value FROM metadata
         WHERE name = 'tracing_started_ns' LIMIT 1)) / 1e9, 3) AS start_offset_seconds
FROM trace_bounds;

SELECT 'trace_quality' AS section, name, idx, severity, value
FROM stats WHERE (value > 0 AND (
  severity IN ('error', 'data_loss')
  OR name IN ('traced_buf_bytes_overwritten', 'traced_buf_chunks_overwritten',
              'ftrace_setup_errors')
)) OR name = 'traced_final_flush_succeeded';

SELECT 'window' AS section, layer_name, COUNT(*) AS frames,
       ROUND(PERCENTILE(dur / 1e6, 50), 3) AS render_p50_ms,
       ROUND(PERCENTILE(dur / 1e6, 95), 3) AS render_p95_ms,
       ROUND(PERCENTILE(budget_ns / 1e6, 50), 3) AS expected_budget_p50_ms,
       COUNT(present_ts) AS matched_presentations,
       ROUND(PERCENTILE((present_ts - ts) / 1e6, 50), 3) AS to_present_p50_ms,
       ROUND(PERCENTILE((present_ts - ts) / 1e6, 95), 3) AS to_present_p95_ms
FROM bc_frame GROUP BY layer_name;

WITH first_frames AS (
  SELECT *, ROW_NUMBER() OVER (PARTITION BY layer_name ORDER BY ts, id) AS n
  FROM bc_frame
)
-- This is the first observed frame, not proof of a first draw if tracing
-- started after the window already existed.
SELECT 'first_observed_frame' AS section, layer_name,
       ROUND((ts - (SELECT start_ts FROM trace_bounds)) / 1e9, 3) AS seconds,
       ROUND(dur / 1e6, 3) AS render_ms,
       ROUND((present_ts - ts) / 1e6, 3) AS to_present_ms,
       jank_type
FROM first_frames WHERE n = 1 ORDER BY ts;

-- Buffer Stuffing alone is a high-latency state, not a dropped-frame count.
SELECT 'classification' AS section, layer_name, jank_type, present_type,
       on_time_finish, prediction_type, COUNT(*) AS frames
FROM bc_frame GROUP BY 2, 3, 4, 5, 6 ORDER BY layer_name, frames DESC;

WITH gaps AS (
  SELECT *, present_ts - LAG(present_ts) OVER (
    PARTITION BY layer_name ORDER BY present_ts, surface_frame_token
  ) AS gap_ns
  FROM bc_frame WHERE present_ts IS NOT NULL
)
SELECT 'presentation_gaps' AS section, layer_name, COUNT(gap_ns) AS intervals,
       ROUND(PERCENTILE(gap_ns / 1e6, 50), 3) AS p50_ms,
       ROUND(PERCENTILE(gap_ns / 1e6, 95), 3) AS p95_ms,
       SUM(gap_ns BETWEEN 12500000 AND 100000000) AS gaps_12_5_to_100_ms,
       SUM(gap_ns > 100000000) AS gaps_over_100_ms,
       ROUND(MAX(gap_ns) / 1e6, 3) AS max_gap_ms
FROM gaps GROUP BY layer_name;

SELECT 'thread_states' AS section, t.tid, t.name, st.state,
       ROUND(SUM(st.dur) / 1e6, 3) AS wall_ms,
       ROUND(MAX(st.dur) / 1e6, 3) AS max_interval_ms
FROM thread_state st JOIN thread t USING (utid) JOIN bc_process p USING (upid)
WHERE st.dur >= 0 AND (t.tid = p.pid OR t.name = 'RenderThread')
GROUP BY t.tid, t.name, st.state ORDER BY t.tid, wall_ms DESC;

-- Sections are nested: sums must not be added together as CPU time.
SELECT 'thread_sections' AS section, thread_name, phase, COUNT(*) AS calls,
       ROUND(PERCENTILE(dur / 1e6, 50), 3) AS p50_ms,
       ROUND(PERCENTILE(dur / 1e6, 95), 3) AS p95_ms,
       ROUND(MAX(dur) / 1e6, 3) AS max_ms
FROM bc_thread_slice
WHERE phase IN ('Choreographer#doFrame', 'DrawFrames', 'traversal',
                'animation', 'Recomposer:recompose', 'AndroidOwner:measureAndLayout',
                'Record View#draw()', 'postAndWait', 'flush commands',
                'RenderProxy#copyHWBitmapInto readback', 'dequeueBuffer',
                'queueBuffer', 'dispatchFrameCallbacks')
GROUP BY thread_name, phase ORDER BY thread_name, p95_ms DESC;

SELECT 'long_main_sections' AS section,
       ROUND((ts - (SELECT start_ts FROM trace_bounds)) / 1e9, 3) AS seconds,
       name, ROUND(dur / 1e6, 3) AS wall_ms
FROM bc_thread_slice
WHERE tid = pid AND dur >= 8000000
ORDER BY dur DESC LIMIT 35;

-- CPU/sleep attribution within long top-level cleanup sections. The slices
-- name a Compose cleanup phase; untraced method identities remain unknown.
WITH cleanup AS (
  SELECT c.* FROM bc_thread_slice c
  LEFT JOIN slice parent ON parent.id = c.parent_id
  WHERE c.tid = c.pid AND c.name = 'Compose:onForgotten' AND c.dur >= 20000000
    AND COALESCE(parent.name, '') != 'Compose:onForgotten'
)
SELECT 'cleanup_states' AS section, c.id,
       ROUND((c.ts - (SELECT start_ts FROM trace_bounds)) / 1e9, 6) AS seconds,
       ROUND(c.dur / 1e6, 3) AS wall_ms,
       st.state,
       ROUND(SUM(MAX(0, MIN(st.ts + st.dur, c.ts + c.dur) - MAX(st.ts, c.ts))) / 1e6, 3) AS state_ms
FROM cleanup c
JOIN thread_state st ON st.utid = c.utid
 AND st.dur >= 0 AND st.ts < c.ts + c.dur AND st.ts + st.dur > c.ts
GROUP BY c.id, st.state ORDER BY c.ts, state_ms DESC;

SELECT 'pipeline_compilation' AS section, tid, thread_name, COUNT(*) AS calls,
       ROUND(SUM(dur) / 1e6, 3) AS total_wall_ms,
       ROUND(MAX(dur) / 1e6, 3) AS max_ms
FROM bc_thread_slice WHERE name = 'CreateGraphicsPipeline'
GROUP BY tid, thread_name;

SELECT 'system_classification' AS section, COALESCE(p.name, t.name) AS process,
       p.pid, f.jank_type, f.present_type, COUNT(*) AS frames
FROM actual_frame_timeline_slice f
JOIN process p USING (upid)
LEFT JOIN thread t ON t.upid = p.upid AND t.tid = p.pid
WHERE f.upid NOT IN (SELECT upid FROM bc_process)
GROUP BY 2, 3, 4, 5 ORDER BY frames DESC LIMIT 20;
