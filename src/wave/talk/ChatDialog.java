package wave.talk;

import java.io.*;
import android.text.Editable;
import android.app.Activity;
import android.app.TabActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.text.method.*;
import android.view.View.OnClickListener;
import android.graphics.drawable.Drawable;

import android.content.Context;
import android.os.Message;
import android.os.Handler;
import org.w3c.dom.*;
import wave.slot.SlotWait;
import wave.slot.SlotRunner;
import android.util.Base64;
import android.app.Dialog;
import android.app.AlertDialog;

public class ChatDialog extends Dialog implements OnClickListener
{

	private Jabber talk;

	public ChatDialog(Context context) {
		super(context);
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		setContentView(R.layout.mychator);

		Button button = (Button)findViewById(R.id.okButton);
		button.setOnClickListener(this);
		button = (Button)findViewById(R.id.cancelButton);
		button.setOnClickListener(this);
    }

	public void setTalk(Jabber talk) {
		this.talk = talk;
	}

	String jid;
	public void setPeer(String jid) {
		this.jid = jid;
	}

    public void onDestory() {

    }

	public void sendMessage() {
		EditText edit = (EditText)findViewById(R.id.nameEditText);
		String title = edit.getText().toString();
		talk.sendMessage(jid, title);
	}

	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.okButton:
				sendMessage();
				dismiss();
				break;

			case R.id.cancelButton:
				cancel();
				break;
		}
	}
}

