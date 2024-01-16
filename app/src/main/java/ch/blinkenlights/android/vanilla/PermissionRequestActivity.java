/*
 * Copyright (C) 2015-2023 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package ch.blinkenlights.android.vanilla;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class PermissionRequestActivity extends Activity {

	/**
	 * The intent to start after acquiring the required permissions
	 */
	private Intent mCallbackIntent;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mCallbackIntent = getIntent().getExtras().getParcelable("callbackIntent");
		requestFileAccessPermissions(mCallbackIntent);
	}

	/**
	 * Called by Activity after the user interacted with the permission request
	 * Will launch the main activity if all permissions were granted, exits otherwise
	 *
	 * @param requestCode The code set by requestPermissions
	 * @param permissions Names of the permissions we got granted or denied
	 * @param grantResults Results of the permission requests
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		List<String> neededPerms = getNeededPermissions();
		int grantedPermissions = 0;

		for (int i = 0; i < permissions.length; i++) {
			if (!neededPerms.contains(permissions[i]))
				continue;
			if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
				grantedPermissions++;
		}

		finishAffinity();

		if (grantedPermissions == neededPerms.size()) {
			if (mCallbackIntent != null) {
				// start the old intent but ensure to make it a new task & clear any old attached activites
				mCallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				startActivity(mCallbackIntent);
			}
		}
	}

	/**
	 * Injects a warning that we are missing read permissions into the activity layout
	 *
	 * @param activity Reference to LibraryActivity
	 * @param intent The intent starting the parent activity
	 */
	public static void showWarning(final LibraryActivity activity, final Intent intent) {
		LayoutInflater inflater = LayoutInflater.from(activity);
		View view = inflater.inflate(R.layout.permission_request, null, false);

		view.setOnClickListener(v -> PermissionRequestActivity.requestPermissions(activity, intent));

		ViewGroup parent = activity.findViewById(R.id.content); // main layout of library_content
		parent.addView(view, -1);
	}

	/**
	 * Launches a permission request dialog if needed
	 *
	 * @param activity The activitys context to use for the permission check
	 * @return boolean true if we showed a permission request dialog
	 */ 
	public static boolean requestPermissions(Activity activity, Intent callbackIntent) {
		boolean havePermissions = havePermissions(activity);

		if(!havePermissions) {
			Intent intent = new Intent(activity, PermissionRequestActivity.class);
			intent.putExtra("callbackIntent", callbackIntent);
			activity.startActivity(intent);
		}

		return !havePermissions;
	}

	/**
	 * Checks if all required permissions have been granted
	 *
	 * @param context The context to use
	 * @return boolean true if all permissions have been granded
	 */
	public static boolean havePermissions(Context context) {
		boolean ok = true;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ok = Environment.isExternalStorageManager();
		for(String permission : getNeededPermissions())
			if(context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) ok = false;
		return ok;
	}

	private static List<String> getNeededPermissions() {
		List<String> l = new ArrayList<>();
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			l.add(Manifest.permission.READ_MEDIA_AUDIO);
			l.add(Manifest.permission.READ_MEDIA_IMAGES);
		}
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			l.add(Manifest.permission.READ_EXTERNAL_STORAGE);
			l.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
		}
		return l;
	}

	private static List<String> getOptionalPermissions() {
		List<String> l = new ArrayList<>();
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
			l.add(Manifest.permission.POST_NOTIFICATIONS);
		return l;
	}

	private void requestFileAccessPermissions(Intent callbackIntent) {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			if(Environment.isExternalStorageManager()) resetFromMainActivity();
			else {
				Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
				Uri uri = Uri.fromParts("package", getPackageName(), null);
				intent.setData(uri);
				startActivityForResult(intent, 111222333);
			}
		}
		else /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)*/ {
			List<String> allPerms = getNeededPermissions();
			allPerms.addAll(getOptionalPermissions());
			requestPermissions(allPerms.toArray(new String[0]), 0);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == 111222333) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				if(!Environment.isExternalStorageManager()) {
					Toast.makeText(this, "All-files permissions not granted, exiting", Toast.LENGTH_SHORT).show();
					finishAffinity();
				}
				else {
					Toast.makeText(this, "All-files permissions granted", Toast.LENGTH_SHORT).show();
					resetFromMainActivity();
				}
			}
		}
	}

	public void resetFromMainActivity() {
		Intent intent = new Intent(this, LibraryActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
	}
}
