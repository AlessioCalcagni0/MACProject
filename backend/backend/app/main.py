from fastapi import FastAPI, Depends, HTTPException, Body, Query
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import or_
from sqlalchemy.orm import Session
from datetime import datetime
import uuid

from .database import engine, ensure_schema, get_db, wait_for_database
from . import models, schemas
from .firebase_auth import (
    get_firebase_user_by_email,
    get_firebase_user_by_uid,
    list_firebase_users,
    verify_firebase_bearer_token,
)


app = FastAPI()

# ================ CONFIGURAZIONE PERMESSI (CORS) ================
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
def startup() -> None:
    wait_for_database()
    models.Base.metadata.create_all(bind=engine)
    ensure_schema()


@app.get("/")
def root():
    return {"message": "Backend running"}

@app.get("/debug/firebase")
def debug_firebase():
    import os

    path = os.getenv("FIREBASE_CREDENTIALS_PATH")

    return {
        "firebase_credentials_path": path,
        "exists": os.path.exists(path) if path else False,
    }

def firebase_uid_to_uuid(firebase_uid: str | None) -> uuid.UUID:
    if not firebase_uid:
        # Fallback to a random UUID if uid is missing, though it shouldn't happen
        return uuid.uuid4()
    return uuid.uuid5(uuid.NAMESPACE_URL, str(firebase_uid))


def split_full_name(full_name: str | None) -> tuple[str | None, str | None]:
    normalized_full_name = (full_name or "").strip()
    if not normalized_full_name:
        return None, None

    parts = normalized_full_name.split()
    if len(parts) == 1:
        return parts[0], None

    return parts[0], " ".join(parts[1:])


def derive_name_fallback(
    email: str | None = None,
    display_name: str | None = None,
    uid: str | None = None,
) -> str:
    for candidate in (display_name, email, uid):
        normalized_candidate = (candidate or "").strip()
        if not normalized_candidate:
            continue

        if "@" in normalized_candidate:
            normalized_candidate = normalized_candidate.split("@", 1)[0].strip()

        if normalized_candidate:
            return normalized_candidate

    return "User"


def normalize_optional_text(value: object) -> str | None:
    if value is None:
        return None

    normalized_value = str(value).strip()
    return normalized_value or None


def build_display_name(
    name: str | None,
    surname: str | None,
    fallback_display_name: str | None = None,
    fallback_email: str | None = None,
    fallback_uid: str | None = None,
) -> str:
    full_name = " ".join(part for part in [name, surname] if part)
    if full_name:
        return full_name

    return fallback_display_name or fallback_email or fallback_uid or ""


def extract_user_profile(firebase_user: dict) -> tuple[str | None, str | None, str]:
    token_display_name = (
        firebase_user.get("display_name")
        or firebase_user.get("name")
    )
    parsed_name, parsed_surname = split_full_name(token_display_name)

    name = (
        firebase_user.get("given_name")
        or firebase_user.get("first_name")
        or parsed_name
    )
    surname = (
        firebase_user.get("family_name")
        or firebase_user.get("last_name")
        or firebase_user.get("surname")
        or parsed_surname
    )
    if not name:
        name = derive_name_fallback(
            email=firebase_user.get("email"),
            display_name=token_display_name,
            uid=firebase_user.get("uid"),
        )

    display_name = build_display_name(
        name=name,
        surname=surname,
        fallback_display_name=token_display_name,
        fallback_email=firebase_user.get("email"),
        fallback_uid=firebase_user.get("uid"),
    )
    return name, surname, display_name


def serialize_group(group: models.Group, db: Session) -> dict:
    members_ids = group.members_ids or []
    members_names = {}
    if members_ids:
        users = db.query(models.User).filter(models.User.firebase_uid.in_(members_ids)).all()
        for user in users:
            members_names[user.firebase_uid] = user.display_name or user.email or "Unknown"

    return {
        "group_id": group.id,
        "name": group.name,
        "creator_id": str(group.creator_id) if group.creator_id else None,
        "members_ids": members_ids,
        "members_names": members_names,
        "created_at": group.created_at.isoformat() if group.created_at else None,
    }


def sync_user_from_firebase(firebase_user: dict, db: Session) -> models.User:
    resolved_name, resolved_surname, resolved_display_name = extract_user_profile(firebase_user)
    uid = firebase_user["uid"]

    user = (
        db.query(models.User)
        .filter(models.User.firebase_uid == uid)
        .first()
    )

    if not user:
        user = models.User(
            name=resolved_name,
            surname=resolved_surname,
            firebase_uid=uid,
            derived_uuid=firebase_uid_to_uuid(uid),
            email=firebase_user.get("email"),
            display_name=resolved_display_name,
            provider=firebase_user.get("provider", "firebase"),
        )
        db.add(user)
    else:
        user.name = resolved_name or user.name or derive_name_fallback(
            email=firebase_user.get("email"),
            display_name=firebase_user.get("display_name") or firebase_user.get("name"),
            uid=uid,
        )
        user.surname = resolved_surname
        user.email = firebase_user.get("email")
        user.display_name = resolved_display_name
        user.provider = firebase_user.get("provider", "firebase")
        user.derived_uuid = firebase_uid_to_uuid(uid)

    db.commit()
    db.refresh(user)
    return user


def serialize_user(user: models.User) -> dict:
    return {
        "firebase_uid": user.firebase_uid,
        "name": user.name,
        "surname": user.surname,
        "email": user.email,
        "display_name": build_display_name(
            name=user.name,
            surname=user.surname,
            fallback_display_name=user.display_name,
            fallback_email=user.email,
            fallback_uid=user.firebase_uid,
        ),
        "weight": user.weight
    }


def add_friend_connection(user: models.User, friend_firebase_uid: str) -> None:
    friend_ids = list(user.friend_ids or [])
    if friend_firebase_uid not in friend_ids:
        friend_ids.append(friend_firebase_uid)
        user.friend_ids = friend_ids


def get_or_sync_user_by_firebase_uid(firebase_uid: str, db: Session) -> models.User:
    user = db.query(models.User).filter(models.User.firebase_uid == firebase_uid).first()
    if user:
        return user

    firebase_user = get_firebase_user_by_uid(firebase_uid)
    return sync_user_from_firebase(firebase_user, db)


def get_group_by_id(group_id: str, db: Session) -> models.Group:
    try:
        group_uuid = uuid.UUID(group_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid group_id format")

    group = db.query(models.Group).filter(models.Group.id == group_uuid).first()
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")
    return group


def get_group_invite_by_id(invite_id: str, db: Session) -> models.GroupInvite:
    try:
        invite_uuid = uuid.UUID(invite_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid invite_id format")

    invite = db.query(models.GroupInvite).filter(models.GroupInvite.id == invite_uuid).first()
    if not invite:
        raise HTTPException(status_code=404, detail="Invite not found")
    return invite


def serialize_group_invite(
    invite: models.GroupInvite,
    invited_user: models.User | None,
    invited_by: models.User | None,
    group: models.Group | None,
) -> dict:
    return {
        "id": invite.id,
        "group_id": invite.group_id,
        "group_name": group.name if group else None,
        "status": invite.status,
        "invited_user": serialize_user(invited_user) if invited_user else None,
        "invited_by": serialize_user(invited_by) if invited_by else None,
        "created_at": invite.created_at.isoformat() if invite.created_at else None,
    }


@app.post("/auth/firebase-sync", response_model=schemas.SyncUserResponse)
def firebase_sync(
    firebase_user: dict = Depends(verify_firebase_bearer_token),
    db: Session = Depends(get_db),
):
    firebase_uid = firebase_user["uid"]
    email = firebase_user.get("email")
    resolved_name, resolved_surname, resolved_display_name = extract_user_profile(firebase_user)

    provider = "firebase"
    identities = firebase_user.get("firebase", {}).get("identities", {})
    sign_in_provider = firebase_user.get("firebase", {}).get("sign_in_provider")

    if sign_in_provider:
        provider = sign_in_provider
    elif identities:
        provider = next(iter(identities.keys()))

    user = (
        db.query(models.User)
        .filter(models.User.firebase_uid == firebase_uid)
        .first()
    )

    if not user:
        user = models.User(
            name=resolved_name,
            surname=resolved_surname,
            firebase_uid=firebase_uid,
            derived_uuid=firebase_uid_to_uuid(firebase_uid),
            email=email,
            display_name=resolved_display_name,
            provider=provider,
            weight=70.0
        )
        db.add(user)
    else:
        user.name = resolved_name or user.name or derive_name_fallback(
            email=email,
            display_name=firebase_user.get("display_name") or firebase_user.get("name"),
            uid=firebase_uid,
        )
        user.surname = resolved_surname
        user.email = email
        user.display_name = resolved_display_name
        user.provider = provider
        user.derived_uuid = firebase_uid_to_uuid(firebase_uid)

    db.commit()
    db.refresh(user)
    return user


@app.post("/users/sync", response_model=schemas.SyncUserResponse)
def users_sync(
    userData: schemas.SyncUserPayload = Body(...),
    firebase_user: dict = Depends(verify_firebase_bearer_token),
    db: Session = Depends(get_db),
):
    firebase_uid = firebase_user["uid"]
    token_email = firebase_user.get("email")
    token_name, token_surname, token_display_name = extract_user_profile(firebase_user)

    provider = "firebase"
    identities = firebase_user.get("firebase", {}).get("identities", {})
    sign_in_provider = firebase_user.get("firebase", {}).get("sign_in_provider")

    if sign_in_provider:
        provider = sign_in_provider
    elif identities:
        provider = next(iter(identities.keys()))

    resolved_email = normalize_optional_text(userData.email) or token_email
    resolved_name = normalize_optional_text(userData.name) or token_name
    resolved_surname = normalize_optional_text(userData.surname) or token_surname
    resolved_weight = userData.weight if userData.weight is not None else 70.0
    resolved_display_name = (
        normalize_optional_text(userData.display_name)
        or build_display_name(
            name=resolved_name,
            surname=resolved_surname,
            fallback_display_name=token_display_name,
            fallback_email=resolved_email,
            fallback_uid=firebase_uid,
        )
    )

    user = (
        db.query(models.User)
        .filter(models.User.firebase_uid == firebase_uid)
        .first()
    )

    if not user:
        user = models.User(
            name=resolved_name or derive_name_fallback(
                email=resolved_email,
                display_name=resolved_display_name,
                uid=firebase_uid,
            ),
            surname=resolved_surname,
            firebase_uid=firebase_uid,
            derived_uuid=firebase_uid_to_uuid(firebase_uid),
            email=resolved_email,
            display_name=resolved_display_name,
            provider=provider,
            weight=resolved_weight
        )
        db.add(user)
    else:
        user.name = resolved_name or user.name or derive_name_fallback(
            email=resolved_email,
            display_name=resolved_display_name,
            uid=firebase_uid,
        )
        user.surname = resolved_surname
        user.email = resolved_email
        user.display_name = resolved_display_name
        user.provider = provider
        user.derived_uuid = firebase_uid_to_uuid(firebase_uid)
        if userData.weight is not None:
            user.weight = userData.weight

    db.commit()
    db.refresh(user)
    return user


@app.get("/auth/firebase-users", response_model=list[schemas.FirebaseUserResponse])
def get_firebase_users(limit: int = 100):
    if limit < 1 or limit > 1000:
        raise HTTPException(status_code=400, detail="limit must be between 1 and 1000")

    return list_firebase_users(page_size=limit)


# ================ FRIENDS ================


@app.post("/friends/request", response_model=schemas.FriendRequestActionResponse)
def send_friend_request(
    from_user_id: str,
    to_user_id: str | None = None,
    to_user_email: str | None = None,
    db: Session = Depends(get_db),
):
    from_user = get_or_sync_user_by_firebase_uid(from_user_id, db)

    if not to_user_id and not to_user_email:
        raise HTTPException(
            status_code=400,
            detail="Provide to_user_id or to_user_email",
        )

    to_user = None
    if to_user_id:
        to_user = (
            db.query(models.User)
            .filter(models.User.firebase_uid == to_user_id)
            .first()
        )
    elif to_user_email:
        # Cerca via email in modo case-insensitive
        target_email = to_user_email.strip().lower()
        to_user = (
            db.query(models.User)
            .filter(models.User.email.ilike(target_email))
            .first()
        )
        if not to_user:
            try:
                firebase_target = get_firebase_user_by_email(to_user_email)
                to_user = sync_user_from_firebase(firebase_target, db)
            except HTTPException as e:
                if e.status_code == 404:
                     raise HTTPException(status_code=404, detail="Target user not found in Firebase")
                raise e

    if not to_user:
        raise HTTPException(
            status_code=404,
            detail="Target user is not registered in the app",
        )

    if from_user.firebase_uid == to_user.firebase_uid:
        raise HTTPException(status_code=400, detail="You cannot add yourself")

    existing_friend_ids = from_user.friend_ids or []
    if to_user.firebase_uid in existing_friend_ids:
        raise HTTPException(status_code=400, detail="Users are already friends")

    existing_request = (
        db.query(models.FriendRequest)
        .filter(
            or_(
                (
                    (models.FriendRequest.from_user_firebase_uid == from_user.firebase_uid)
                    & (models.FriendRequest.to_user_firebase_uid == to_user.firebase_uid)
                ),
                (
                    (models.FriendRequest.from_user_firebase_uid == to_user.firebase_uid)
                    & (models.FriendRequest.to_user_firebase_uid == from_user.firebase_uid)
                ),
            )
        )
        .filter(models.FriendRequest.status == "pending")
        .first()
    )
    if existing_request:
        raise HTTPException(status_code=400, detail="A pending friend request already exists")

    friend_request = models.FriendRequest(
        from_user_firebase_uid=from_user.firebase_uid,
        to_user_firebase_uid=to_user.firebase_uid,
        status="pending",
    )
    db.add(friend_request)
    db.commit()
    db.refresh(friend_request)

    return {
        "status": "pending",
        "request_id": friend_request.id,
        "to_user_id": to_user.firebase_uid
    }


@app.get("/friends/search", response_model=list[schemas.FriendSearchResponse])
def search_friends(query: str, user_id: str | None = None, limit: int = 20, db: Session = Depends(get_db)):
    if limit < 1 or limit > 100:
        raise HTTPException(status_code=400, detail="limit must be between 1 and 100")

    normalized_query = query.strip()
    if not normalized_query:
        return []

    users = (
        db.query(models.User)
        .filter(
            or_(
                models.User.email.ilike(f"%{normalized_query}%"),
                models.User.name.ilike(f"%{normalized_query}%"),
                models.User.surname.ilike(f"%{normalized_query}%"),
                models.User.display_name.ilike(f"%{normalized_query}%"),
                models.User.firebase_uid.ilike(f"%{normalized_query}%"),
            )
        )
        .limit(limit + 1)
        .all()
    )

    return [
        serialize_user(user)
        for user in users
        if not user_id or user.firebase_uid != user_id
    ][:limit]


@app.get("/users/search", response_model=list[schemas.FriendUserResponse])
def search_users(query: str, db: Session = Depends(get_db)):
    """Alias for friends/search, used by RunFragment to find user by UID"""
    return search_friends(query=query, db=db)


@app.get("/friends/pending", response_model=list[schemas.FriendRequestResponse])
def get_pending_friends(user_id: str, db: Session = Depends(get_db)):
    user = get_or_sync_user_by_firebase_uid(user_id, db)

    requests = (
        db.query(models.FriendRequest)
        .filter(models.FriendRequest.to_user_firebase_uid == user.firebase_uid)
        .filter(models.FriendRequest.status == "pending")
        .all()
    )

    if not requests:
        return []

    users_by_id = {
        friend.firebase_uid: friend
        for friend in db.query(models.User)
        .filter(
            models.User.firebase_uid.in_(
                [request.from_user_firebase_uid for request in requests]
                + [request.to_user_firebase_uid for request in requests]
            )
        )
        .all()
    }

    return [
        {
            "id": request.id,
            "status": request.status,
            "from_user": serialize_user(users_by_id[request.from_user_firebase_uid]),
            "to_user": serialize_user(users_by_id[request.to_user_firebase_uid]),
        }
        for request in requests
    ]


@app.get("/notifications/friend-requests", response_model=list[schemas.FriendRequestResponse])
def get_friend_request_notifications(user_id: str, db: Session = Depends(get_db)):
    return get_pending_friends(user_id=user_id, db=db)


@app.post("/friends/respond", response_model=schemas.FriendRequestActionResponse)
def respond_friend_request(
    request_id: str,
    user_id: str | None = None,
    action: str | None = None,
    status: str | None = None,
    db: Session = Depends(get_db),
):
    raw_decision = (action or status or "").lower().strip()
    decision_map = {
        "accept": "accepted",
        "accepted": "accepted",
        "reject": "rejected",
        "rejected": "rejected",
    }
    normalized_action = decision_map.get(raw_decision)
    if not normalized_action:
        raise HTTPException(
            status_code=400,
            detail="Provide action/status as accept, accepted, reject, or rejected",
        )

    try:
        request_uuid = uuid.UUID(request_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid request_id format")

    friend_request = (
        db.query(models.FriendRequest)
        .filter(models.FriendRequest.id == request_uuid)
        .first()
    )
    if not friend_request:
        raise HTTPException(status_code=404, detail="Friend request not found")

    effective_user_id = user_id or friend_request.to_user_firebase_uid

    if friend_request.to_user_firebase_uid != effective_user_id:
        raise HTTPException(status_code=403, detail="This request does not belong to the user")

    if friend_request.status != "pending":
        raise HTTPException(status_code=400, detail="Friend request already handled")

    if normalized_action == "accepted":
        from_user = get_or_sync_user_by_firebase_uid(friend_request.from_user_firebase_uid, db)
        to_user = get_or_sync_user_by_firebase_uid(friend_request.to_user_firebase_uid, db)
        add_friend_connection(from_user, to_user.firebase_uid)
        add_friend_connection(to_user, from_user.firebase_uid)
        friend_request.status = "accepted"
    else:
        friend_request.status = "rejected"

    friend_request.responded_at = datetime.utcnow()
    db.commit()
    db.refresh(friend_request)

    return {"status": friend_request.status, "request_id": friend_request.id}


@app.get("/friends/list", response_model=list[schemas.FriendUserResponse])
def get_friends_list(user_id: str, db: Session = Depends(get_db)):
    user = get_or_sync_user_by_firebase_uid(user_id, db)
    friend_ids = user.friend_ids or []

    if not friend_ids:
        return []

    friends = (
        db.query(models.User)
        .filter(models.User.firebase_uid.in_(friend_ids))
        .all()
    )

    return [serialize_user(friend) for friend in friends]


# ================ RUN ================


@app.get("/runs")
def get_all_runs(db: Session = Depends(get_db)):
    """Get all runs"""
    runs = db.query(models.Run).all()
    return runs


@app.get("/runs/{run_id}")
def get_run(run_id: str, db: Session = Depends(get_db)):
    """Get a specific run by ID"""
    try:
        run_uuid = uuid.UUID(run_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid run_id format")

    run = db.query(models.Run).filter(models.Run.id == run_uuid).first()
    if not run:
        raise HTTPException(status_code=404, detail="Run not found")
    return run


@app.post("/runs/start")
def start_run(user_id: str, db: Session = Depends(get_db)):
    try:
        # Check if user_id is already a UUID
        user_uuid = uuid.UUID(user_id)
    except ValueError:
        user = get_or_sync_user_by_firebase_uid(user_id, db)
        user_uuid = user.derived_uuid or firebase_uid_to_uuid(user.firebase_uid)

    run = models.Run(id=uuid.uuid4(), user_id=user_uuid)
    db.add(run)
    db.commit()
    db.refresh(run)
    return {"run_id": str(run.id)}


@app.get("/runs/{run_id}/route")
def get_route_points(run_id: str, db: Session = Depends(get_db)):
    """Get all route points for a run"""
    try:
        run_uuid = uuid.UUID(run_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid run_id format")

    points = db.query(models.RoutePoint).filter(models.RoutePoint.run_id == run_uuid).all()
    return points


@app.post("/runs/{run_id}/route")
def add_point(run_id: str, lat: float, lng: float, db: Session = Depends(get_db)):
    # Converti run_id da stringa a UUID
    try:
        run_uuid = uuid.UUID(run_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid run_id format")

    point = models.RoutePoint(
        run_id=run_uuid,
        latitude=lat,
        longitude=lng
    )
    db.add(point)
    db.commit()
    return {"status": "ok"}


@app.post("/runs/{run_id}/end")
def end_run(run_id: str, distance: float, db: Session = Depends(get_db)):
    # Converti run_id da stringa a UUID
    try:
        run_uuid = uuid.UUID(run_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid run_id format")

    run = db.query(models.Run).filter(models.Run.id == run_uuid).first()

    if not run:
        raise HTTPException(status_code=404, detail="Run not found")

    run.end_time = datetime.utcnow()
    run.distance = distance

    # Calcolo durata in secondi
    duration = (run.end_time - run.start_time).total_seconds()
    run.duration = int(duration)

    # Calcolo velocità media (km/h)
    if duration > 0:
        run.avg_speed = distance / (duration / 3600.0)
    else:
        run.avg_speed = 0

    # Calcolo calorie in base al peso (default 70 se non trovato)
    # Search user by derived_uuid
    user = db.query(models.User).filter(models.User.derived_uuid == run.user_id).first()
    if not user:
        # Fallback if derived_uuid is not set yet
        users = db.query(models.User).all()
        user = next((u for u in users if firebase_uid_to_uuid(u.firebase_uid) == run.user_id), None)

    weight = user.weight if user else 70.0
    run.calories = distance * weight * 0.9

    db.commit()

    return {"status": "completed"}


# ================ PHOTO ================


@app.get("/photos")
def get_all_photos(db: Session = Depends(get_db)):
    """Get all photos"""
    photos = db.query(models.Photo).all()
    return photos


@app.get("/runs/{run_id}/photos")
def get_run_photos(run_id: str, db: Session = Depends(get_db)):
    """Get all photos for a run"""
    try:
        run_uuid = uuid.UUID(run_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid run_id format")

    photos = db.query(models.Photo).filter(models.Photo.run_id == run_uuid).all()
    return photos


@app.post("/photos")
def add_photo(run_id: str, image_url: str, latitude: float, longitude: float, db: Session = Depends(get_db)):
    """Add a photo to a run"""
    try:
        run_uuid = uuid.UUID(run_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid run_id format")

    photo = models.Photo(
        run_id=run_uuid,
        image_url=image_url,
        latitude=latitude,
        longitude=longitude
    )
    db.add(photo)
    db.commit()
    db.refresh(photo)
    return {"photo_id": str(photo.id)}


# ================ GROUP ================


@app.get("/groups", response_model=list[schemas.GroupResponse])
def get_all_groups(db: Session = Depends(get_db)):
    """Get all groups"""
    groups = db.query(models.Group).all()
    return [serialize_group(group, db) for group in groups]


@app.get("/groups/{group_id}", response_model=schemas.GroupResponse)
def get_group(group_id: str, db: Session = Depends(get_db)):
    """Get a specific group by ID"""
    try:
        group_uuid = uuid.UUID(group_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid group_id format")

    group = db.query(models.Group).filter(models.Group.id == group_uuid).first()
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")
    return serialize_group(group, db)


@app.post("/groups")
@app.post("/groups/create")
def create_group(
    name: str | None = None,
    creator_id: str | None = None,
    payload: schemas.GroupCreateRequest | None = Body(default=None),
    db: Session = Depends(get_db),
):
    """Create a new group"""
    resolved_name = payload.name if payload and payload.name else name
    resolved_creator_id = payload.creator_id if payload and payload.creator_id else creator_id

    if not resolved_name or not resolved_creator_id:
        raise HTTPException(status_code=400, detail="name and creator_id are required")

    creator = get_or_sync_user_by_firebase_uid(resolved_creator_id, db)

    group = models.Group(
        name=resolved_name,
        creator_id=creator.firebase_uid,
        members_ids=[creator.firebase_uid]
    )
    db.add(group)
    db.commit()
    db.refresh(group)
    return {"group_id": str(group.id)}


@app.put("/groups/{group_id}")
async def update_group(group_id: str, name: str | None = Query(None), payload: dict = Body(None), db: Session = Depends(get_db)):
    """
    Aggiorna il gruppo. Accetta il nome sia come Query parameter (?name=...)
    sia nel Body JSON ({"name": "...", "members_ids": [...]}).
    """
    group = get_group_by_id(group_id, db)

    # Prende il nome o dalla query o dal body
    new_name = name or (payload.get("name") if payload else None)
    if new_name:
        group.name = new_name

    # Sincronizza la lista dei membri se fornita nel body
    if payload and "members_ids" in payload:
        group.members_ids = payload["members_ids"]

    db.commit()
    return {"status": "ok", "message": "Group updated"}


@app.post("/groups/{group_id}/members/{user_id}")
def add_group_member(group_id: str, user_id: str, db: Session = Depends(get_db)):
    """Aggiunge un membro direttamente al gruppo (senza invito)."""
    group = get_group_by_id(group_id, db)
    members = list(group.members_ids or [])
    if user_id not in members:
        members.append(user_id)
        group.members_ids = members
        db.commit()
    return {"status": "ok", "message": "Member added"}


@app.delete("/groups/{group_id}")
def delete_group(group_id: str, db: Session = Depends(get_db)):
    group = get_group_by_id(group_id, db)
    db.delete(group)
    db.commit()
    return {"status": "ok"}


@app.delete("/groups/{group_id}/members/{user_id}")
def remove_group_member(group_id: str, user_id: str, db: Session = Depends(get_db)):
    group = get_group_by_id(group_id, db)
    members = list(group.members_ids or [])
    if user_id in members:
        members.remove(user_id)
        group.members_ids = members
        db.commit()
    return {"status": "ok"}


@app.get("/groups/{group_id}/start-run")
@app.post("/groups/{group_id}/start-run")
def start_group_run(group_id: str, organizer_id: str, db: Session = Depends(get_db)):
    print(f"[BACKEND] Inizio corsa di gruppo: {group_id} by {organizer_id}")
    try:
        group_uuid = uuid.UUID(group_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid group_id format")

    group = db.query(models.Group).filter(models.Group.id == group_uuid).first()
    if not group:
        raise HTTPException(status_code=404, detail="Group not found")

    return {
        "group_id": str(group.id),
        "group_name": group.name,
        "members": group.members_ids or []
    }


@app.post("/groups/{group_id}/invites", response_model=schemas.GroupInviteActionResponse)
def create_group_invite(
    group_id: str,
    from_user_id: str | None = None,
    to_user_id: str | None = None,
    payload: schemas.GroupInviteCreateRequest | None = Body(default=None),
    db: Session = Depends(get_db),
):
    resolved_from_user_id = payload.from_user_id if payload and payload.from_user_id else from_user_id
    resolved_to_user_id = payload.to_user_id if payload and payload.to_user_id else to_user_id

    if not resolved_from_user_id or not resolved_to_user_id:
        raise HTTPException(status_code=400, detail="from_user_id and to_user_id are required")

    group = get_group_by_id(group_id, db)
    from_user = get_or_sync_user_by_firebase_uid(resolved_from_user_id, db)
    to_user = get_or_sync_user_by_firebase_uid(resolved_to_user_id, db)

    if from_user.firebase_uid == to_user.firebase_uid:
        raise HTTPException(status_code=400, detail="You cannot invite yourself")

    if group.members_ids and from_user.firebase_uid not in group.members_ids:
        raise HTTPException(status_code=403, detail="Only group members can invite friends")

    friend_ids = from_user.friend_ids or []
    if to_user.firebase_uid not in friend_ids:
        # Fallback per permettere inviti se l'utente esiste ma non è ancora nella lista amici
        pass

    if to_user.firebase_uid in (group.members_ids or []):
        raise HTTPException(status_code=400, detail="User is already a member of the group")

    existing_invite = (
        db.query(models.GroupInvite)
        .filter(models.GroupInvite.group_id == group.id)
        .filter(models.GroupInvite.user_id == (to_user.derived_uuid or firebase_uid_to_uuid(to_user.firebase_uid)))
        .filter(models.GroupInvite.status == "pending")
        .first()
    )
    if existing_invite:
        raise HTTPException(status_code=400, detail="A pending group invite already exists")

    invite = models.GroupInvite(
        group_id=group.id,
        user_id=(to_user.derived_uuid or firebase_uid_to_uuid(to_user.firebase_uid)),
        group_run_id=None,
        invited_by_firebase_uid=from_user.firebase_uid,
        status="pending",
    )
    db.add(invite)
    db.commit()
    db.refresh(invite)

    return {"status": "pending", "invite_id": invite.id, "group_id": group.id}


# ================ GROUP RUN ================


@app.get("/group-runs")
def get_all_group_runs(db: Session = Depends(get_db)):
    """Get all group runs"""
    group_runs = db.query(models.GroupRun).all()
    return group_runs


@app.get("/group-runs/{group_run_id}")
def get_group_run(group_run_id: str, db: Session = Depends(get_db)):
    """Get a specific group run by ID"""
    try:
        group_run_uuid = uuid.UUID(group_run_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid group_run_id format")

    group_run = db.query(models.GroupRun).filter(models.GroupRun.id == group_run_uuid).first()
    if not group_run:
        raise HTTPException(status_code=404, detail="Group run not found")
    return group_run


@app.get("/groups/{group_id}/runs")
def get_group_runs(group_id: str, db: Session = Depends(get_db)):
    """Get all runs for a group"""
    try:
        group_uuid = uuid.UUID(group_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid group_id format")

    group_runs = db.query(models.GroupRun).filter(models.GroupRun.group_id == group_uuid).all()
    return group_runs


@app.post("/group-runs/start")
def start_group_run_legacy(group_id: str, organizer_id: str, db: Session = Depends(get_db)):
    try:
        group_uuid = uuid.UUID(group_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid group_id format")

    organizer = get_or_sync_user_by_firebase_uid(organizer_id, db)
    organizer_uuid = organizer.derived_uuid or firebase_uid_to_uuid(organizer.firebase_uid)

    group = db.query(models.Group).filter(models.Group.id == group_uuid).first()

    if not group:
        raise HTTPException(status_code=404, detail="Group not found")

    group_run = models.GroupRun(
        group_id=group_uuid,
        organizer_id=organizer_uuid,
        status="pending"
    )

    db.add(group_run)
    db.commit()
    db.refresh(group_run)

    # Invito automatico dei membri del gruppo
    for member_id in group.members_ids or []:
        if not isinstance(member_id, str):
            continue

        member = db.query(models.User).filter(models.User.firebase_uid == member_id).first()
        if member:
            invite_user_id = member.derived_uuid or firebase_uid_to_uuid(member.firebase_uid)
        else:
            try:
                invite_user_id = uuid.UUID(member_id)
            except ValueError:
                continue

        invite = models.GroupInvite(
            group_run_id=group_run.id,
            group_id=group_uuid,
            user_id=invite_user_id,
            invited_by_firebase_uid=organizer.firebase_uid,
            status="pending"
        )
        db.add(invite)

    db.commit()

    return {"group_run_id": str(group_run.id)}


# ================ INVITE RESPONSE ================


@app.get("/group-invites")
def get_all_invites(db: Session = Depends(get_db)):
    """Get all group invites"""
    invites = db.query(models.GroupInvite).all()
    return invites


@app.get("/group-invites/{invite_id}")
def get_invite(invite_id: str, db: Session = Depends(get_db)):
    """Get a specific invite by ID"""
    return get_group_invite_by_id(invite_id, db)


@app.get("/group-runs/{group_run_id}/invites")
def get_group_run_invites(group_run_id: str, db: Session = Depends(get_db)):
    """Get all invites for a group run"""
    try:
        group_run_uuid = uuid.UUID(group_run_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid group_run_id format")

    invites = db.query(models.GroupInvite).filter(models.GroupInvite.group_run_id == group_run_uuid).all()
    return invites


@app.get("/users/{user_id}/invites", response_model=list[schemas.GroupInviteResponse])
def get_user_invites(user_id: str, db: Session = Depends(get_db)):
    """Get all invites for a user identified by Firebase UID"""
    user = get_or_sync_user_by_firebase_uid(user_id, db)
    user_uuid = user.derived_uuid or firebase_uid_to_uuid(user.firebase_uid)
    invites = db.query(models.GroupInvite).filter(models.GroupInvite.user_id == user_uuid).all()

    groups = {
        group.id: group
        for group in db.query(models.Group)
        .filter(models.Group.id.in_([invite.group_id for invite in invites if invite.group_id]))
        .all()
    } if invites else {}

    inviter_ids = [invite.invited_by_firebase_uid for invite in invites if invite.invited_by_firebase_uid]
    inviters = {
        inviter.firebase_uid: inviter
        for inviter in db.query(models.User)
        .filter(models.User.firebase_uid.in_(inviter_ids))
        .all()
    } if inviter_ids else {}

    response = []
    for invite in invites:
        response.append(
            serialize_group_invite(
                invite,
                user,
                inviters.get(invite.invited_by_firebase_uid),
                groups.get(invite.group_id),
            )
        )

    return response


@app.get("/groups/invites/pending", response_model=list[schemas.GroupInviteResponse])
@app.get("/group-invites/pending", response_model=list[schemas.GroupInviteResponse])
def get_pending_group_invites(user_id: str, db: Session = Depends(get_db)):
    user = get_or_sync_user_by_firebase_uid(user_id, db)
    user_uuid = user.derived_uuid or firebase_uid_to_uuid(user.firebase_uid)
    invites = (
        db.query(models.GroupInvite)
        .filter(models.GroupInvite.user_id == user_uuid)
        .filter(models.GroupInvite.status.like("pending%"))
        .all()
    )

    if not invites:
        return []

    group_ids = [invite.group_id for invite in invites if invite.group_id]
    groups = {
        group.id: group
        for group in db.query(models.Group).filter(models.Group.id.in_(group_ids)).all()
    }

    inviter_ids = [
        invite.invited_by_firebase_uid
        for invite in invites
        if invite.invited_by_firebase_uid
    ]
    inviters = {
        inviter.firebase_uid: inviter
        for inviter in db.query(models.User)
        .filter(models.User.firebase_uid.in_(inviter_ids))
        .all()
    } if inviter_ids else {}

    return [
        serialize_group_invite(
            invite=invite,
            invited_user=user,
            invited_by=inviters.get(invite.invited_by_firebase_uid),
            group=groups.get(invite.group_id),
        )
        for invite in invites
    ]


@app.post("/group-invites/respond", response_model=schemas.GroupInviteActionResponse)
def respond_invite(
    invite_id: str | None = None,
    user_id: str | None = None,
    status: str | None = None,
    payload: schemas.GroupInviteRespondRequest | None = Body(default=None),
    db: Session = Depends(get_db),
):
    resolved_invite_id = payload.invite_id if payload and payload.invite_id else invite_id
    resolved_user_id = payload.user_id if payload and payload.user_id else user_id
    resolved_status = payload.status if payload and payload.status else status

    if not resolved_invite_id or not resolved_user_id or not resolved_status:
        raise HTTPException(status_code=400, detail="invite_id, user_id and status are required")

    invite = get_group_invite_by_id(resolved_invite_id, db)
    user = get_or_sync_user_by_firebase_uid(resolved_user_id, db)

    if invite.user_id != (user.derived_uuid or firebase_uid_to_uuid(user.firebase_uid)):
        raise HTTPException(status_code=403, detail="This invite does not belong to the user")

    normalized_status = resolved_status.lower().strip()
    if normalized_status not in {"accepted", "rejected"}:
        raise HTTPException(status_code=400, detail="status must be accepted or rejected")

    if invite.status == "accepted" or invite.status == "rejected":
        raise HTTPException(status_code=400, detail="Invite already handled")

    invite.status = normalized_status
    invite.response_time = int(datetime.utcnow().timestamp())

    if normalized_status == "accepted" and invite.group_id:
        group = db.query(models.Group).filter(models.Group.id == invite.group_id).first()
        if not group:
            raise HTTPException(status_code=404, detail="Group not found")

        members_ids = list(group.members_ids or [])
        if user.firebase_uid not in members_ids:
            members_ids.append(user.firebase_uid)
            group.members_ids = members_ids

    db.commit()

    return {"status": invite.status, "invite_id": invite.id, "group_id": invite.group_id}
