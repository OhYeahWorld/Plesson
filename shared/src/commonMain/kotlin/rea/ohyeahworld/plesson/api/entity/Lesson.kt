package rea.ohyeahworld.plesson.api.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Lesson (
    @SerialName("day") var day: String?,
    @SerialName("period") var period: String?,
    @SerialName("discipline") var discipline: String?,
    @SerialName("workload_type") var workload_type: String?,
    @SerialName("room") var room: String?,
    @SerialName("building") var building: String?,
    @SerialName("campus") var campus: String?,
    @SerialName("teacher_name") var teacher_name: String?,
    @SerialName("instructor_departments") var instructor_departments: List<String?>
)