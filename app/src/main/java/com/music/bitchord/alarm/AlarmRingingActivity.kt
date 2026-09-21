package com.music.bitchord.alarm
import android.app.PendingIntent
import android.content.*
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.ui.theme.BitChordTheme
import java.text.DateFormat
import java.util.Date

class AlarmRingingActivity:AppCompatActivity(){private var id="";private var token=""
 override fun onCreate(b:Bundle?){super.onCreate(b);setShowWhenLocked(true);setTurnScreenOn(true);id=intent.getStringExtra(ID).orEmpty();token=intent.getStringExtra(TOKEN).orEmpty();AlarmStore.init(this);setContent{BitChordTheme{val state by AlarmStore.alarms.collectAsState();val alarm=state.alarms.firstOrNull{it.id==id};if(alarm==null||state.activeSession?.token!=token){LaunchedEffect(Unit){finishAndRemoveTask()}}else Ringing(alarm,{send(AlarmReceiver.snoozePendingIntent(this,id,token))},{send(AlarmReceiver.stopPendingIntent(this,id,token))})}}}
 private fun send(p:PendingIntent){runCatching{p.send()};finishAndRemoveTask()}
 override fun dispatchKeyEvent(e:KeyEvent):Boolean{if(e.keyCode!=KeyEvent.KEYCODE_VOLUME_UP&&e.keyCode!=KeyEvent.KEYCODE_VOLUME_DOWN)return super.dispatchKeyEvent(e);val alarm=AlarmStore.current(this).alarms.firstOrNull{it.id==id}?:return super.dispatchKeyEvent(e);if(e.action==KeyEvent.ACTION_DOWN){when(volumeButtonCommand(alarm.volumeButtonAction,e.repeatCount==0)){AlarmVolumeButtonCommand.SNOOZE->send(AlarmReceiver.snoozePendingIntent(this,id,token));AlarmVolumeButtonCommand.STOP->send(AlarmReceiver.stopPendingIntent(this,id,token));AlarmVolumeButtonCommand.ADJUST_ALARM_VOLUME->getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_ALARM,if(e.keyCode==KeyEvent.KEYCODE_VOLUME_UP)AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);else->{}}};return true}
 companion object{private const val ID="alarm_id";private const val TOKEN="token";fun intent(c:Context,id:String,token:String)=PendingIntent.getActivity(c,id.hashCode(),Intent(c,AlarmRingingActivity::class.java).putExtra(ID,id).putExtra(TOKEN,token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)}
}
@Composable private fun Ringing(a:AlarmConfig,snooze:()->Unit,stop:()->Unit){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground){Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),style=MaterialTheme.typography.displayLarge);if(a.label.isNotBlank())Text(a.label,style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(28.dp));AsyncImage(a.song?.artworkUrl,null,Modifier.fillMaxWidth().aspectRatio(1f));Spacer(Modifier.height(20.dp));Text(a.song?.title.orEmpty(),style=MaterialTheme.typography.titleLarge,textAlign=TextAlign.Center);Text(a.song?.artist.orEmpty(),color=MaterialTheme.colorScheme.onSurfaceVariant)};Column{Button(snooze,Modifier.fillMaxWidth().height(72.dp),shape=RoundedCornerShape(24.dp)){Text(stringResource(R.string.alarm_snooze_duration,a.snoozeMinutes))};Spacer(Modifier.height(16.dp));FilledTonalButton(stop,Modifier.fillMaxWidth().height(64.dp)){Text(stringResource(R.string.alarm_stop))}}}}}
