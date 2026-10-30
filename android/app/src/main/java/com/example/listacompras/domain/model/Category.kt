package com.example.listacompras.domain.model

/** Item category. [slug] is the value stored in the PocketBase `category` select field. */
enum class Category(val slug: String) {
    OILS_SAUCES("oils_sauces"),
    DRINKS("drinks"),
    MEAT("meat"),
    DELI("deli"),
    FROZEN("frozen"),
    FRUIT("fruit"),
    PERSONAL_CARE("personal_care"),
    DAIRY("dairy"),
    CLEANING("cleaning"),
    SWEET_PANTRY("sweet_pantry"),
    SAVORY_PANTRY("savory_pantry"),
    BAKERY("bakery"),
    FISH_SEAFOOD("fish_seafood"),
    PET("pet"),
    BABY("baby"),
    SNACKS("snacks"),
    HOUSEHOLD("household"),
    VEGETABLES("vegetables"),
    OTHER("other");

    companion object {
        /** Unknown or blank slugs map to null so a new server value never breaks a read. */
        fun fromSlug(slug: String?): Category? = entries.firstOrNull { it.slug == slug }
    }
}
