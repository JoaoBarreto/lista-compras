package com.example.listacompras.data.repository

/** Id of the logged-in user, needed for `added_by` / `checked_by`. */
interface CurrentUserProvider {
    fun currentUserId(): String?
}
