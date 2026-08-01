package com.quocdat.lingolens.common

class EmailAlreadyExistsException(message: String) : RuntimeException(message)
class TokenExpiredException(message: String) : RuntimeException(message)
class InvalidTokenException(message: String) : RuntimeException(message)
class UserNotFoundException(message: String) : RuntimeException(message)
class AccountDisabledException(message: String) : RuntimeException(message)
