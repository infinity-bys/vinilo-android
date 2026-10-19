package com.infinitybys.vinilo

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

object MusicRepository {
    fun loadSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            collection, projection, selection, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                songs += Song(
                    id = id,
                    title = cursor.getString(titleColumn) ?: "Canción desconocida",
                    artist = cursor.getString(artistColumn)?.takeIf { it != "<unknown>" } ?: "Artista desconocido",
                    album = cursor.getString(albumColumn) ?: "",
                    duration = cursor.getLong(durationColumn),
                    uri = ContentUris.withAppendedId(collection, id)
                )
            }
        }
        return songs
    }
}
