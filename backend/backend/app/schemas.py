from pydantic import BaseModel
from uuid import UUID


class SyncUserResponse(BaseModel):
    id: int
    firebase_uid: str
    name: str | None
    surname: str | None
    email: str | None
    display_name: str | None
    provider: str

    class Config:
        from_attributes = True


class SyncUserPayload(BaseModel):
    name: str | None = None
    surname: str | None = None
    display_name: str | None = None
    email: str | None = None


class FirebaseUserResponse(BaseModel):
    uid: str
    email: str | None
    display_name: str | None
    provider: str
    disabled: bool


class FriendUserResponse(BaseModel):
    firebase_uid: str
    name: str | None
    surname: str | None
    email: str | None
    display_name: str | None


class FriendRequestResponse(BaseModel):
    id: UUID
    status: str
    from_user: FriendUserResponse
    to_user: FriendUserResponse


class FriendSearchResponse(BaseModel):
    firebase_uid: str
    name: str | None
    surname: str | None
    email: str | None
    display_name: str | None


class FriendRequestActionResponse(BaseModel):
    status: str
    request_id: UUID


class GroupInviteUserResponse(BaseModel):
    firebase_uid: str
    name: str | None
    surname: str | None
    email: str | None
    display_name: str | None


class GroupInviteResponse(BaseModel):
    id: UUID
    group_id: UUID
    group_name: str | None
    status: str
    invited_user: GroupInviteUserResponse
    invited_by: GroupInviteUserResponse
    created_at: str | None


class GroupInviteActionResponse(BaseModel):
    status: str
    invite_id: UUID
    group_id: UUID


class GroupCreateRequest(BaseModel):
    name: str
    creator_id: str


class GroupResponse(BaseModel):
    group_id: UUID
    name: str | None
    creator_id: UUID | None
    members_ids: list[str] | None
    created_at: str | None


class GroupInviteCreateRequest(BaseModel):
    from_user_id: str
    to_user_id: str


class GroupInviteRespondRequest(BaseModel):
    invite_id: str
    user_id: str
    status: str


class RunCreate(BaseModel):
    user_id: UUID
    distance: float
    duration: int
    calories: int
    avg_speed: float


class RunResponse(BaseModel):
    id: UUID
    user_id: UUID
    distance: float
    duration: int
    calories: int
    avg_speed: float

    class Config:
        from_attributes = True
