import html
import os
import subprocess
import sys

import requests


def get_git_commit_info():
    commit_author = subprocess.check_output(
        ["git", "log", "-1", "--pretty=format:%an"], text=True
    )
    commit_message = subprocess.check_output(
        ["git", "log", "-1", "--pretty=format:%s"], text=True
    )
    commit_hash = subprocess.check_output(
        ["git", "log", "-1", "--pretty=format:%H"], text=True
    )
    commit_hash_short = subprocess.check_output(
        ["git", "log", "-1", "--pretty=format:%h"], text=True
    )
    return commit_author, commit_message, commit_hash, commit_hash_short


def build_telegram_message(commit_author, commit_message, commit_hash, commit_hash_short):
    safe_author = html.escape(commit_author, quote=False)
    safe_commit_message = html.escape(commit_message, quote=False)

    return (
        f'A new <a href="https://github.com/sketchlibx/SketchwareNeo/commit/{commit_hash}">commit</a> '
        f'has been merged to the repository by <b>{safe_author}</b>.\n\n'
        f'<b>What has changed:</b>\n'
        f'<blockquote>{safe_commit_message}</blockquote>\n\n'
        "I'm currently building two APK variants and will send both here if the build is successful:\n"
        "- Android 8.0+ (API 26+)\n"
        "- Android 13+ (API 33+)\n\n"
        f"#{commit_hash_short}"
    )


def main():
    bot_token = os.environ["BOT_TOKEN"]
    chat_id = os.environ["CHAT_ID"]
    topic_id = os.environ.get("TOPIC_ID")

    commit_author, commit_message, commit_hash, commit_hash_short = get_git_commit_info()
    message = build_telegram_message(
        commit_author, commit_message, commit_hash, commit_hash_short
    )

    url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
    payload = {
        "chat_id": chat_id,
        "text": message,
        "parse_mode": "HTML",
        "disable_web_page_preview": True,
    }
    if topic_id:
        payload["message_thread_id"] = int(topic_id) if topic_id.isdigit() else topic_id

    try:
        response = requests.post(url, json=payload, timeout=20)
    except requests.RequestException as error:
        print(f"Failed to send message: {error}")
        return 1

    try:
        result = response.json()
    except ValueError:
        print(f"Failed to send message: Telegram returned HTTP {response.status_code} with an invalid JSON response.")
        return 1

    if response.status_code != 200 or not result.get("ok"):
        description = result.get("description", response.text or "Unknown Telegram API error")
        print(f"Failed to send message: HTTP {response.status_code}: {description}")
        return 1

    print("Message sent successfully.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
