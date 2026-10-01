import os

def resolve_file(filepath, resolution_func):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    parts = []
    lines = content.splitlines(keepends=True)
    i = 0
    resolved_content = ''
    while i < len(lines):
        if lines[i].startswith('<<<<<<< HEAD'):
            i += 1
            head_lines = []
            while not lines[i].startswith('======='):
                head_lines.append(lines[i])
                i += 1
            i += 1
            upstream_lines = []
            while not lines[i].startswith('>>>>>>>'):
                upstream_lines.append(lines[i])
                i += 1
            i += 1
            
            resolved_content += resolution_func(''.join(head_lines), ''.join(upstream_lines), filepath)
        else:
            resolved_content += lines[i]
            i += 1
            
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(resolved_content)

def resolver(head, upstream, filepath):
    if 'MainActivity.kt' in filepath:
        return head
    elif 'SourceRegistry.kt' in filepath:
        if 'val enabled: Boolean = true' in head:
            return upstream.rstrip() + '\n    val username: String = "",\n    val password: String = "",\n'
        else:
            return upstream
    elif 'SourceResolver.kt' in filepath:
        if 'val pinned' in head:
            res = upstream
            res += '        if (strict) {\n'
            res += '            if (pinned != null) {\n'
            res += '                return attempt(pinned) { pinned.stream(trackId, request) }\n'
            res += '                    ?.copy(sourceConfigId = pinned.configId)\n'
            res += '            }\n'
            res += '            return null\n'
            res += '        }\n\n'
            return res
        else:
            return head
    elif 'MainViewModel.kt' in filepath:
        return head
    elif 'AccountAlerts.kt' in filepath:
        return head
    elif 'HomeScreen.kt' in filepath:
        return head + upstream
    elif 'SourcesScreen.kt' in filepath:
        return head
    return head

files = [
    'app/src/main/java/com/music/bitchord/MainActivity.kt',
    'app/src/main/java/com/music/bitchord/data/sources/SourceRegistry.kt',
    'app/src/main/java/com/music/bitchord/data/sources/SourceResolver.kt',
    'app/src/main/java/com/music/bitchord/ui/MainViewModel.kt',
    'app/src/main/java/com/music/bitchord/ui/components/AccountAlerts.kt',
    'app/src/main/java/com/music/bitchord/ui/screens/HomeScreen.kt',
    'app/src/main/java/com/music/bitchord/ui/screens/SourcesScreen.kt'
]

for file in files:
    resolve_file(file, resolver)

print('Conflicts resolved automatically.')
