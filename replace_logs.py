import os
import re

def replace_in_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    if 'printStackTrace()' not in content:
        return False

    # Add import if missing
    if 'import com.example.core.logger.AppLogger' not in content:
        content = content.replace('package com.example', 'import com.example.core.logger.AppLogger\n\npackage com.example')

    # Replace printStackTrace
    # Assuming the catch block is 'catch (e: Exception)' or similar
    # This regex is simple, might need adjustment if catch blocks are complex
    content = re.sub(r'(\w+)\.printStackTrace\(\)', r'AppLogger.e(message="Error", throwable=\1)', content)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    return True

files = [
    "app/src/main/java/com/example/feature/chat/presentation/ChatViewModel.kt",
    "app/src/main/java/com/example/features/stickers/studio/StickerStudioRenderer.kt",
    "app/src/main/java/com/example/features/stickers/studio/GifEncoder.kt",
    "app/src/main/java/com/example/ui/screen/ReelEditorScreen.kt",
    "app/src/main/java/com/example/ui/components/SimpleVideoPreviewPlayer.kt",
    "app/src/main/java/com/example/ui/components/chat/voice/AudioWaveformAnalyzer.kt",
    "app/src/main/java/com/example/ui/components/QrCodeView.kt",
    "app/src/main/java/com/example/ui/viewmodel/PendingUploadsViewModel.kt",
    "app/src/main/java/com/example/ui/viewmodel/ProfileViewModel.kt",
    "app/src/main/java/com/example/panatv/PanaTVRepository.kt",
    "app/src/main/java/com/example/data/repository/YouTubeRepository.kt",
    "app/src/main/java/com/example/data/database/PanalinkDatabase.kt",
    "app/src/main/java/com/example/util/CameraPermissionManager.kt",
    "app/src/main/java/com/example/util/MediaCompositionEngine.kt",
    "app/src/main/java/com/example/media/audio/AudioImportManager.kt"
]

for file_path in files:
    if os.path.exists(file_path):
        if replace_in_file(file_path):
            print(f"Updated {file_path}")
