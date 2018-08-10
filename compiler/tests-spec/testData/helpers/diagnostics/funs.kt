fun _fun(value1: List<Int>, value2: List<Int>): Int {
    return value1[0] + value2[1]
}

fun _fun(value1: List<Int>): Int {
    return value1[0] + value2[1]
}

fun _fun(): Int {
    return Any().hashCode().toInt()
}
