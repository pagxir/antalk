package com.zhuri.talk;

import android.net.Uri;
import android.database.Cursor;
import android.content.Context;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.content.ContentProvider;

public class JabberContent extends ContentProvider {
	ContentResolver resolver;

	public void done() {
/*
		resolver = getContentResolver();
		Cursor cs = resolver.query(Book.CONTENT_URI, null, null, null, Book.ID + " ASC");
		CursorAdapter adapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_list_item_2, cs, new String[] {
						Book.TITLE, Book.PRICE }, new int[] {
						android.R.id.text1, android.R.id.text2 });
		lvBooks.setAdapter(adapter);
*/
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection,
			String selection,  String[] selectionArgs, String sortOrder) {
		return null;
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}  

	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		return uri;
	}  

	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		return 0;
	}

	@Override
	public int update(Uri uri, ContentValues values,
			String where, String[] whereArgs) {
		return 0;
	} 
}

