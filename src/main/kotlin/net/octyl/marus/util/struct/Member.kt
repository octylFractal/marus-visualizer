package net.octyl.marus.util.struct

data class Member<T>(
    val name: String,
    val type: MemberType<T>
)

fun floatMember(name: String) = Member(name, MemberType.Float)
