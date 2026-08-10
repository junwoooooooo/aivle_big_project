from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


FingerprintDimension = Literal[
    "targetUsers",
    "problemScenario",
    "coreValue",
    "solutionMechanism",
    "revenueModel",
    "channels",
    "platformRole",
    "operatingModel",
    "partnerModel",
    "transactionFlow",
    "providerRole",
    "sellerRole",
    "intermediaryRole",
    "featureSet",
    "actorRoles",
    "price",
    "paymentFlow",
    "personalDataUsage",
    "physicalActivities",
    "partnerRequirements",
    "qualificationRequirements",
]

BUSINESS_FINGERPRINT_FIELDS = FingerprintDimension.__args__


class BusinessFingerprint(BaseModel):
    """BusinessFingerprint v1 shared by candidate history and distinctness inputs."""

    model_config = ConfigDict(extra="forbid")

    targetUsers: str = Field(min_length=1, max_length=1000)
    problemScenario: str = Field(min_length=1, max_length=2000)
    coreValue: str = Field(min_length=1, max_length=2000)
    solutionMechanism: str = Field(min_length=1, max_length=3000)
    revenueModel: str = Field(min_length=1, max_length=1000)
    channels: str = Field(min_length=1, max_length=1000)
    platformRole: str = Field(min_length=1, max_length=1000)
    operatingModel: str = Field(min_length=1, max_length=2000)
    partnerModel: str = Field(min_length=1, max_length=2000)
    transactionFlow: list[str] = Field(min_length=1, max_length=20)
    providerRole: str = Field(min_length=1, max_length=1000)
    sellerRole: str = Field(min_length=1, max_length=1000)
    intermediaryRole: str = Field(min_length=1, max_length=1000)
    featureSet: list[str] = Field(min_length=1, max_length=30)
    actorRoles: list[str] = Field(min_length=1, max_length=20)
    price: str = Field(min_length=1, max_length=1000)
    paymentFlow: list[str] = Field(min_length=1, max_length=20)
    personalDataUsage: list[str] = Field(max_length=20)
    physicalActivities: list[str] = Field(max_length=20)
    partnerRequirements: list[str] = Field(max_length=20)
    qualificationRequirements: list[str] = Field(max_length=20)
