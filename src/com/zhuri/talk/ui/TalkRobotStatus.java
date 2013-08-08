package com.zhuri.talk.ui;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;
import java.util.Enumeration;
import java.util.Set;
import java.util.HashSet;

import com.zhuri.talk.R;
import com.zhuri.talk.TalkService;
import com.zhuri.talk.PstcpService;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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

	private AdapterView.OnItemClickListener mItemClick =
		new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent,
					View view, int pos, long id) {
				Toast.makeText(TalkRobotStatus.this,
						mStrings[pos], Toast.LENGTH_SHORT).show();
				Intent intent = new Intent("talk.intent.action.SEND");
				intent.putExtra("to", mStrings[pos]);
				intent.putExtra("message", "stun");
				TalkRobotStatus.this.sendBroadcast(intent);
				return;
			}
		};

	private String[] mStrings = new String[]{ };
	private Set<String> mAllPeer = new HashSet<String>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Button start = (Button)findViewById(R.id.start);
		start.setOnClickListener(this);

		Button stop = (Button)findViewById(R.id.stop);
		stop.setOnClickListener(this);

		ListView list = (ListView)findViewById(R.id.user_list);
		list.setOnItemClickListener(mItemClick);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, mStrings);
		list.setAdapter(adapter);

		return;
	}

	BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (action.equals("talk.intent.action.MESSAGE")) {
				TextView view = (TextView)findViewById(R.id.location);
				view.setText(intent.getStringExtra("message"));
			} else if (action.equals("talk.intent.action.PRESENCE")) {
				String type = intent.getStringExtra("type");
				String from = intent.getStringExtra("from");
				if (type.equals("unavailable")) {
					mAllPeer.remove(intent.getStringExtra("from"));
				} else {
					mAllPeer.add(intent.getStringExtra("from"));
				}

				String[] newPeers = new String[mAllPeer.size()];
				mAllPeer.toArray(newPeers);
				ListView list = (ListView)findViewById(R.id.user_list);
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(TalkRobotStatus.this,
						android.R.layout.simple_list_item_1, newPeers);
				mStrings = newPeers;
				list.setAdapter(adapter);
			}
		}
	};

	@Override
	public void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter();
		filter.addAction("talk.intent.action.MESSAGE");
		filter.addAction("talk.intent.action.PRESENCE");
		registerReceiver(mReceiver, filter);
	}

	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(mReceiver);
	}

	private String mProvider = null;
	private void startLocationListen(String provider) {
		Criteria criteria = new Criteria();
		criteria.setCostAllowed(true);
		criteria.setAltitudeRequired(false);
		criteria.setBearingRequired(false);
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setPowerRequirement(Criteria.POWER_LOW);

		mProvider = provider;
		mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		mLocationManager.requestLocationUpdates(provider, 5000, 5, mLocationListener);

		Location location = mLocationManager.getLastKnownLocation(provider);
		if (location != null)
			showLocation(location);

		return;
	}

	private void showLocation(Location location) {
		double altitude  = location.getAltitude();
		double latitude  = location.getLatitude();
		double longitude = location.getLongitude();

		StringBuilder builder = new StringBuilder();
		if (mProvider != null) {
			builder.append("provider: ");
			builder.append(mProvider);
			builder.append("\n");
		}
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
		if (mLocationManager != null)
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
		super.onCreateOptionsMenu(menu);
		MenuItem item1 = 
			menu.add(Menu.NONE, Menu.FIRST + 1, 1, R.string.settings)
			.setIcon(android.R.drawable.ic_menu_edit);

		MenuItem item2 = 
			menu.add(Menu.NONE, Menu.FIRST + 2, 2, R.string.network_provider)
			.setIcon(android.R.drawable.ic_menu_day);

		MenuItem item3 = 
			menu.add(Menu.NONE, Menu.FIRST + 3, 3, R.string.gps_provider)
			.setIcon(android.R.drawable.ic_menu_agenda);

		MenuItem item4 = 
			menu.add(Menu.NONE, Menu.FIRST + 4, 4, R.string.passive_provider)
			.setIcon(android.R.drawable.ic_menu_camera);

		return true;
    }   

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case Menu.FIRST + 1:
                Intent settings = new Intent(this, TalkRobotSettings.class);
                startActivity(settings);
                break;

			case Menu.FIRST + 2:
				Toast.makeText(this, "Network Provider", Toast.LENGTH_SHORT).show();
				if (mLocationManager != null)
					mLocationManager.removeUpdates(mLocationListener);
				startLocationListen(LocationManager.NETWORK_PROVIDER);
				break;

			case Menu.FIRST + 3:
				Toast.makeText(this, "GPS Provider", Toast.LENGTH_SHORT).show();
				if (mLocationManager != null)
					mLocationManager.removeUpdates(mLocationListener);
				startLocationListen(LocationManager.GPS_PROVIDER);
				break;

			case Menu.FIRST + 4:
				Toast.makeText(this, "Passive Provider", Toast.LENGTH_SHORT).show();
				if (mLocationManager != null)
					mLocationManager.removeUpdates(mLocationListener);
				startLocationListen(LocationManager.PASSIVE_PROVIDER);
				break;
        }   

        return false;
    }   
}
