from telethon import TelegramClient
import os
import subprocess


def get_git_commit_info():
    commit_author = subprocess.check_output(
        ["git", "log", "-1", "--pretty=format:%an"]
    ).decode("utf-8")
    commit_message = subprocess.check_output(
        ["git", "log", "-1", "--pretty=format:%s"]
    ).decode("utf-8")
    commit_hash_short = subprocess.check_output(
        ["git", "log", "-1", "--pretty=format:%h"]
    ).decode("utf-8")
    return commit_author, commit_message, commit_hash_short


api_id = int(os.environ["API_ID"])
api_hash = os.environ["API_HASH"]
bot_token = os.environ["BOT_TOKEN"]
group_id = int(os.environ["CHAT_ID"])
topic_value = os.getenv("TOPIC_ID", "").strip()
reply_to = int(topic_value) if topic_value else None

apk_targets = [
    (os.getenv("APK_PATH_API26"), "Android 8.0+ (API 26+)"),
    (os.getenv("APK_PATH_API33"), "Android 13+ (API 33+)"),
]
# Backward compatibility for manually triggered runs that still provide APK_PATH.
if not any(path for path, _ in apk_targets):
    legacy_path = os.getenv("APK_PATH")
    if legacy_path:
        apk_targets = [(legacy_path, "Android APK")]

commit_author, commit_message, commit_hash_short = get_git_commit_info()
session_file = "bot_session.session"
if os.path.exists(session_file):
    os.remove(session_file)

client = TelegramClient("bot_session", api_id, api_hash).start(bot_token=bot_token)
client.parse_mode = "markdown"


def human_readable_size(size, decimal_places=2):
    for unit in ["B", "KB", "MB", "GB", "TB"]:
        if size < 1024.0:
            break
        size /= 1024.0
    return f"{size:.{decimal_places}f} {unit}"


async def progress(current, total):
    if total <= 0:
        return
    progress_percentage = (current / total) * 100
    uploaded_size_readable = human_readable_size(current)
    total_size_readable = human_readable_size(total)
    print(
        f"{progress_percentage:.2f}% uploaded - "
        f"{uploaded_size_readable}/{total_size_readable}",
        end="\r",
    )


async def send_file(file_path, platform_label, commit_author, commit_message, commit_hash_short):
    if not file_path or not os.path.isfile(file_path) or os.path.getsize(file_path) <= 0:
        raise FileNotFoundError(f"APK missing or empty: {file_path}")

    print(f"Sending {platform_label} APK: {file_path}")
    caption = (
        f"**Commit by:** {commit_author}\n"
        f"**Commit message:** {commit_message}\n"
        f"**Commit hash:** #{commit_hash_short}\n"
        f"**Compatibility:** {platform_label}"
    )
    await client.send_file(
        entity=group_id,
        file=file_path,
        parse_mode="markdown",
        caption=caption,
        progress_callback=progress,
        reply_to=reply_to,
    )
    print(f"\nSent {platform_label} APK successfully")


async def send_all():
    failures = []
    for apk_path, platform_label in apk_targets:
        if not apk_path:
            continue
        try:
            await send_file(
                apk_path, platform_label, commit_author, commit_message, commit_hash_short
            )
        except Exception as exc:
            failures.append(f"{platform_label}: {exc}")
            print(f"Failed to send {platform_label} APK: {exc}")

    if not apk_targets:
        raise RuntimeError("No APK paths were supplied to deploy_artifacts.py")
    if failures:
        raise RuntimeError("Telegram upload failures: " + "; ".join(failures))


try:
    with client:
        client.loop.run_until_complete(send_all())
finally:
    if client.is_connected():
        client.loop.run_until_complete(client.disconnect())
