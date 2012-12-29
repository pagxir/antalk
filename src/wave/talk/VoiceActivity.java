package wave.talk;

import android.os.Bundle;
import android.app.Activity;

public class VoiceActivity extends Activity
{

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.recorder);
	}

	public void onDestroy()
	{
		super.onDestroy();
	}
}

