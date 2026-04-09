from sqlalchemy import Column, String, Float, Integer, DateTime, ForeignKey, Date
from sqlalchemy.dialects.postgresql import UUID, JSON
import uuid
from datetime import datetime
from .database import Base


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String)
    surname = Column(String, nullable=True)
    email = Column(String)
    firebase_uid = Column(String, unique=True)
    derived_uuid = Column(UUID(as_uuid=True), unique=True, nullable=True)
    display_name = Column(String)
    provider = Column(String, default="firebase")
    friend_ids = Column(JSON, default=list)
    weight = Column(Float, default=70.0)
    created_at = Column(DateTime, default=datetime.utcnow)


class FriendRequest(Base):
    __tablename__ = "friend_requests"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    from_user_firebase_uid = Column(String, nullable=False)
    to_user_firebase_uid = Column(String, nullable=False)
    status = Column(String, default="pending")
    created_at = Column(DateTime, default=datetime.utcnow)
    responded_at = Column(DateTime)


class Run(Base):
    __tablename__ = "runs"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(UUID)
    start_time = Column(DateTime, default=datetime.utcnow)
    end_time = Column(DateTime)
    date = Column(Date)
    duration = Column(Integer)
    distance = Column(Float)
    avg_speed = Column(Float)
    calories = Column(Float)
    group_id = Column(String)
    created_at = Column(DateTime, default=datetime.utcnow)


class RoutePoint(Base):
    __tablename__ = "route_points"

    id = Column(Integer, primary_key=True)
    run_id = Column(UUID, ForeignKey("runs.id"))
    latitude = Column(Float)
    longitude = Column(Float)
    timestamp = Column(DateTime, default=datetime.utcnow)


class Photo(Base):
    __tablename__ = "photos"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    run_id = Column(UUID)
    image_url = Column(String)
    latitude = Column(Float)
    longitude = Column(Float)
    created_at = Column(DateTime, default=datetime.utcnow)


class Group(Base):
    __tablename__ = "groups"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(String)
    creator_id = Column(String)
    members_ids = Column(JSON, default=list)
    created_at = Column(DateTime, default=datetime.utcnow)


class GroupRun(Base):
    __tablename__ = "group_runs"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    group_id = Column(UUID)
    organizer_id = Column(UUID)
    cover_photo_url = Column(String)
    status = Column(String)


class GroupInvite(Base):
    __tablename__ = "group_invites"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    group_run_id = Column(UUID)
    group_id = Column(UUID)
    user_id = Column(UUID)
    invited_by_firebase_uid = Column(String)
    status = Column(String)
    created_at = Column(DateTime, default=datetime.utcnow)
    response_time = Column(Integer)
