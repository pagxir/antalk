package com.zhuri.talk.ui;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;
import java.util.Enumeration;

import com.zhuri.talk.R;
import com.zhuri.talk.TalkService;
import com.zhuri.talk.PstcpService;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Menu;
import android.view.MenuItem;

import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;

public class TalkRobotStatus extends Activity implements OnClickListener {
	static final String LOG_TAG ="TalkRobotStatus";
	private LocationManager mLocationManager = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Button start = (Button)findViewById(R.id.start);
		start.setOnClickListener(this);

		Button stop = (Button)findViewById(R.id.stop);
		stop.setOnClickListener(this);

		Criteria criteria = new Criteria();
		criteria.setCostAllowed(true);
		criteria.setAltitudeRequired(false);
		criteria.setBearingRequired(false);
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setPowerRequirement(Criteria.POWER_LOW);

		mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		mLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 5000, 5, mLocationListener);

		Location location = mLocationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
		if (location != null)
			showLocation(location);

		return;
	}

	private void showLocation(Location location) {
		double altitude  = location.getAltitude();
		double latitude  = location.getLatitude();
		double longitude = location.getLongitude();


		StringBuilder builder = new StringBuilder();
		builder.append("altitude: ");
		builder.append(altitude);
		builder.append("\n");
		builder.append("latitude: ");
		builder.append(latitude);
		builder.append("\n");
		builder.append("longitude: ");
		builder.append(longitude);
		builder.append("\n");
		builder.append("https://maps.google.com/maps?ll=" + latitude + "," + longitude + "&spn=0.004710,0.007832&t=k&hl=en");

		TextView view = (TextView)findViewById(R.id.location);
		view.setText(builder.toString());
		return;
	}

	final LocationListener mLocationListener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
			showLocation(location);
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {

		}

		@Override
		public void onProviderEnabled(String provider) {

		}

		@Override
		public void onProviderDisabled(String provider) {

		}
	};

	@Override
	public void onDestroy() {
		super.onDestroy();
		mLocationManager.removeUpdates(mLocationListener);
		return;
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.start:
				startService(PstcpService.serviceIntent(this));
				startService(TalkService.serviceIntent(this));
				break;

			case R.id.stop:
				stopService(TalkService.serviceIntent(this));
				stopService(PstcpService.serviceIntent(this));
				break;

			default:
				break;
		}
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, Menu.FIRST + 1, 5, R.string.settings).setIcon(android.R.drawable.ic_menu_edit);
        return true;
    }   

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case Menu.FIRST + 1:
                Intent settings = new Intent(this, TalkRobotSettings.class);
                startActivity(settings);
                break;
        }   

        return false;
    }   
}
