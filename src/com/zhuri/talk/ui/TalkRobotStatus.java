package com.zhuri.talk.ui;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
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

public class TalkRobotStatus extends Activity implements OnClickListener {
	static final String LOG_TAG ="TalkRobotStatus";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Button start = (Button)findViewById(R.id.start);
		start.setOnClickListener(this);

		Button stop = (Button)findViewById(R.id.stop);
		stop.setOnClickListener(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
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
