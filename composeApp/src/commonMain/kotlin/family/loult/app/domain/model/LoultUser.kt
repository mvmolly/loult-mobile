package family.loult.app.domain.model

data class LoultUser(
    val userId: String,
    val name: String,
    val adjective: String,
    val color: String?,
    val img: String?,
    val profile: Profile?,
    val isYou: Boolean,
)

data class Profile(
    val age: Int? = null,
    val sex: String? = null,
    val orientation: String? = null,
    val job: String? = null,
    val city: String? = null,
    val departement: String? = null,
)
