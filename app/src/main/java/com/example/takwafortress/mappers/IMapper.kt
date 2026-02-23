package com.example.takwafortress.mappers


interface IMapper<INPUT, OUTPUT> {
    fun map(data: INPUT): OUTPUT
    fun reverseMap(data: OUTPUT): INPUT
}