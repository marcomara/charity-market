package it.charitymarket.desktop.api

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    SYSTEM_ADMINISTRATOR,
    MARKET_MANAGER,
    INVENTORY_MANAGER,
    SELLER,
    AUDITOR
}

@Serializable
enum class UserStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED
}

@Serializable
enum class ItemCondition {
    NEW,
    LIKE_NEW,
    GOOD,
    ACCEPTABLE,
    POOR
}

@Serializable
enum class ItemStatus {
    AVAILABLE,
    SOLD,
    DAMAGED,
    MISSING
}

@Serializable
enum class PaymentMethod {
    CASH,
    CARD,
    OTHER
}

@Serializable
enum class SaleStatus {
    COMPLETED,
    VOIDED
}

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
    val confirmation: String
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val passwordChangeRequired: Boolean,
    val user: AuthenticatedUserResponse
)

@Serializable
data class AuthenticatedUserResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String? = null,
    val status: UserStatus,
    val mustChangePassword: Boolean,
    val roles: Set<UserRole>
)

@Serializable
data class TokenInfoResponse(
    val userId: String,
    val username: String,
    val roles: Set<String>
)

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String? = null,
    val status: UserStatus,
    val mustChangePassword: Boolean,
    val roles: Set<UserRole>,
    val createdAt: String,
    val lastLoginAt: String? = null
)

@Serializable
data class CreateUserRequest(
    val username: String,
    val displayName: String,
    val email: String? = null,
    val temporaryPassword: String,
    val roles: Set<UserRole>
)

@Serializable
data class ReplaceRolesRequest(
    val roles: Set<UserRole>
)

@Serializable
data class ApplicationSettingsResponse(
    val currencyCode: String,
    val updatedAt: String,
    val updatedByUserId: String? = null
)

@Serializable
data class UpdateApplicationSettingsRequest(
    val currencyCode: String
)

@Serializable
data class DonorResponse(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val comments: String? = null,
    val createdAt: String,
    val updatedAt: String? = null
)

@Serializable
data class CreateDonorRequest(
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val comments: String? = null
)

@Serializable
data class UpdateDonorRequest(
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val comments: String? = null
)

@Serializable
data class ItemResponse(
    val id: String,
    val code: String,
    val name: String,
    val donorId: String,
    val donorName: String,
    val condition: ItemCondition,
    val suggestedPriceCents: Long,
    val status: ItemStatus,
    val comments: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateItemRequest(
    val code: String? = null,
    val name: String,
    val donorId: String,
    val condition: ItemCondition,
    val suggestedPriceCents: Long,
    val comments: String? = null
)

@Serializable
data class UpdateItemRequest(
    val code: String,
    val name: String,
    val donorId: String,
    val condition: ItemCondition,
    val suggestedPriceCents: Long,
    val comments: String? = null
)

@Serializable
data class SaleLineRequest(
    val itemId: String,
    val finalPriceCents: Long
)

@Serializable
data class CreateSaleRequest(
    val lines: List<SaleLineRequest>,
    val paymentMethod: PaymentMethod,
    val comments: String? = null
)

@Serializable
data class UpdateSaleRequest(
    val lines: List<SaleLineRequest>,
    val paymentMethod: PaymentMethod,
    val comments: String? = null
)

@Serializable
data class VoidSaleRequest(
    val reason: String? = null
)

@Serializable
data class SaleLineResponse(
    val itemId: String,
    val itemCode: String,
    val itemName: String,
    val finalPriceCents: Long
)

@Serializable
data class SaleResponse(
    val id: String,
    val soldAt: String,
    val paymentMethod: PaymentMethod,
    val totalCents: Long,
    val itemCount: Int,
    val lines: List<SaleLineResponse>,
    val status: SaleStatus = SaleStatus.COMPLETED,
    val comments: String? = null,
    val updatedAt: String? = null,
    val voidedAt: String? = null,
    val voidedByUserId: String? = null,
    val voidReason: String? = null
)
