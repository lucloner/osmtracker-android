package net.osmtracker

import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import net.osmtracker.db.TrackContentProvider
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class BTest {

    @Test
    fun testDB1() {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val contentResolver = ContextWrapper(applicationContext).contentResolver
        val cursor: Cursor = contentResolver.query(
                ContentUris.withAppendedId(TrackContentProvider.CONTENT_URI_TRACK, 1),
                null, null, null, null)!!

        Log.e("testDB1", "" + cursor.columnNames.contentToString())
        val c = cursor.columnCount
        while (cursor.moveToNext()) {
            var r = ""
            for (i in 0 until c) {
                r += "" + cursor.getString(i) + ":\t"
            }
            Log.e("testDB1", "" + r)
        }
    }

    @Test
    fun testDB2() {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val contentResolver = ContextWrapper(applicationContext).contentResolver
        val cursor = contentResolver.query(
                TrackContentProvider.trackPointsUri(1),
                null, null, null, TrackContentProvider.Schema.COL_TIMESTAMP + " asc")!!

        Log.e("testDB2", "" + cursor.columnNames.contentToString())
        val c = cursor.columnCount
        while (cursor.moveToNext()) {
            var r = ""
            for (i in 0 until c) {
                r += "" + cursor.getString(i) + ":\t"
            }
            Log.e("testDB2", "" + r)
        }
    }

    @Test
    fun testDB3() {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val contentResolver = ContextWrapper(applicationContext).contentResolver
        val cursor = contentResolver.query(
                TrackContentProvider.CONTENT_URI_TRACK, null, null, null,
                TrackContentProvider.Schema.COL_START_DATE + " desc")!!

        Log.e("testDB3", "" + cursor.columnNames.contentToString())
        val c = cursor.columnCount
        while (cursor.moveToNext()) {
            var r = ""
            for (i in 0 until c) {
                r += "" + cursor.getString(i) + ":\t"
            }
            Log.e("testDB3", "" + r)
        }
    }
}