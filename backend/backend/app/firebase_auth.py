import os
from typing import Any
import logging

import firebase_admin
from fastapi import Header, HTTPException
from firebase_admin import auth, credentials


_firebase_initialized = False
_missing_credentials_logged = False
logger = logging.getLogger(__name__)


def initialize_firebase() -> None:
    global _firebase_initialized, _missing_credentials_logged

    if _firebase_initialized or firebase_admin._apps:
        _firebase_initialized = True
        return

    credentials_path = os.getenv("FIREBASE_CREDENTIALS_PATH")
    if not credentials_path:
        return

    if not os.path.exists(credentials_path):
        if not _missing_credentials_logged:
            logger.warning(
                "Firebase credentials file not found at %s. "
                "Firebase-protected endpoints will stay unavailable until it is mounted.",
                credentials_path,
            )
            _missing_credentials_logged = True
        return

    cred = credentials.Certificate(credentials_path)
    firebase_admin.initialize_app(cred)
    _firebase_initialized = True
    _missing_credentials_logged = False


def verify_firebase_bearer_token(
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    initialize_firebase()

    if not _firebase_initialized:
        raise HTTPException(
            status_code=503,
            detail=(
                "Firebase is not configured. Set FIREBASE_CREDENTIALS_PATH "
                "on the backend."
            ),
        )

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=401,
            detail="Missing or invalid Authorization header",
        )

    token = authorization.removeprefix("Bearer ").strip()
    if not token:
        raise HTTPException(status_code=401, detail="Empty Firebase token")

    try:
        return auth.verify_id_token(token)
    except Exception as exc:
        raise HTTPException(status_code=401, detail="Invalid Firebase token") from exc


def list_firebase_users(page_size: int = 100) -> list[dict[str, Any]]:
    initialize_firebase()

    if not _firebase_initialized:
        raise HTTPException(
            status_code=503,
            detail=(
                "Firebase is not configured. Set FIREBASE_CREDENTIALS_PATH "
                "on the backend."
            ),
        )

    users: list[dict[str, Any]] = []
    page = auth.list_users(max_results=page_size)

    for user in page.users:
        provider = "firebase"
        if user.provider_data:
            provider = user.provider_data[0].provider_id

        users.append(
            {
                "uid": user.uid,
                "email": user.email,
                "display_name": user.display_name,
                "provider": provider,
                "disabled": user.disabled,
            }
        )

    return users


def get_firebase_user_by_uid(uid: str) -> dict[str, Any]:
    initialize_firebase()

    if not _firebase_initialized:
        raise HTTPException(
            status_code=503,
            detail=(
                "Firebase is not configured. Set FIREBASE_CREDENTIALS_PATH "
                "on the backend."
            ),
        )

    try:
        user = auth.get_user(uid)
    except auth.UserNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Firebase user not found") from exc

    provider = "firebase"
    if user.provider_data:
        provider = user.provider_data[0].provider_id

    return {
        "uid": user.uid,
        "email": user.email,
        "display_name": user.display_name,
        "provider": provider,
        "disabled": user.disabled,
    }


def get_firebase_user_by_email(email: str) -> dict[str, Any]:
    initialize_firebase()

    if not _firebase_initialized:
        raise HTTPException(
            status_code=503,
            detail=(
                "Firebase is not configured. Set FIREBASE_CREDENTIALS_PATH "
                "on the backend."
            ),
        )

    try:
        user = auth.get_user_by_email(email)
    except auth.UserNotFoundError as exc:
        raise HTTPException(status_code=404, detail="Firebase user not found") from exc

    provider = "firebase"
    if user.provider_data:
        provider = user.provider_data[0].provider_id

    return {
        "uid": user.uid,
        "email": user.email,
        "display_name": user.display_name,
        "provider": provider,
        "disabled": user.disabled,
    }


def search_firebase_users(query: str, limit: int = 20) -> list[dict[str, Any]]:
    initialize_firebase()

    if not _firebase_initialized:
        raise HTTPException(
            status_code=503,
            detail=(
                "Firebase is not configured. Set FIREBASE_CREDENTIALS_PATH "
                "on the backend."
            ),
        )

    normalized_query = query.strip().lower()
    if not normalized_query:
        return []

    matches: list[dict[str, Any]] = []
    page = auth.list_users()

    for user in page.iterate_all():
        email = (user.email or "").lower()
        display_name = (user.display_name or "").lower()

        if normalized_query not in email and normalized_query not in display_name:
            continue

        provider = "firebase"
        if user.provider_data:
            provider = user.provider_data[0].provider_id

        matches.append(
            {
                "uid": user.uid,
                "email": user.email,
                "display_name": user.display_name,
                "provider": provider,
                "disabled": user.disabled,
            }
        )

        if len(matches) >= limit:
            break

    return matches
