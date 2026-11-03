package com.example.listacompras.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: `pending_ops.collection` (existing rows are items) plus the calendar tables.
 * The SQL is copied from the generated `schemas/2.json`.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `pending_ops` ADD COLUMN `collection` TEXT NOT NULL DEFAULT 'items'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `event_types` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `notes` TEXT, " +
                "`startsAt` INTEGER NOT NULL, `allDay` INTEGER NOT NULL, `typeId` TEXT NOT NULL, `reminder` TEXT, " +
                "`createdBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`syncState` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_startsAt` ON `events` (`startsAt`)")
    }
}
