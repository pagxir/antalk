package wave.talk;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;

import android.widget.Button;

import android.view.View;
import android.view.View.OnClickListener;

public class RecordActivity extends Activity
			implements OnClickListener {

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.start_record:
				session.start();
				break;

			case R.id.stop_record:
				RecordActivity.this.finish();
				break;

			default:
				break;
		}
	}

	PhoneCallSession session = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.recorder);
		session = new PhoneCallSession(getIntent());

		Button start = (Button)findViewById(R.id.start_record);
		start.setText(session.getStartName());
		start.setOnClickListener(this);

		Button stop = (Button)findViewById(R.id.stop_record);
		stop.setOnClickListener(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		session.close();
	}
}

class PhoneCallSession {
	private String action;
	private Intent intent;

	public PhoneCallSession(Intent intent) {
		action = intent.getStringExtra("action");
		STUNPingPong.lock();
		if (action.equals("calling"))
			STUNPingPong.rejected();
		this.intent = intent;
	}

	public String getStartName() {
		boolean calling = action.equals("calling");
		return calling? "呼叫": "接听";
	}

	public void start() {

	}

	public void close() {
		STUNPingPong.unlock();
	}

	public void run() {
		String jid, sid;

		if (play && started) {
		}

		if (!play && playing) {
		}
	}
}

