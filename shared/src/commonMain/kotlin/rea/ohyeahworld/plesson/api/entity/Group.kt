package rea.ohyeahworld.plesson.api.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Group(
    @SerialName("group_guid") var group_guid: String? = null,
    @SerialName("group_name") var group_name: String? = null,
    @SerialName("faculty") var faculty: String? = null,
    @SerialName("program_type") var program_type: String? = null,
    @SerialName("course") var course: String? = null
)
