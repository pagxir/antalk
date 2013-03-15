package com.zhuri.talk;

import org.w3c.dom.Element;
import android.content.Intent;

import com.zhuri.slot.SlotWait;
import com.zhuri.slot.SlotSlot;
import com.zhuri.talk.JabberDaemon;
import com.zhuri.talk.protocol.Jabber;
import com.zhuri.talk.ProtoPlugcan.ProtoPlugin;

public class VoiceCall extends ProtoPlugin {
	SlotWait dialWait = new SlotWait(this);
	static VoiceCall instance = new VoiceCall();
	public final static String VOICE_CALL = "http://www.google.com/voice";

	public static void init() {
		ProtoPlugcan can = ProtoPlugcan.getInstance();
        can.registerPlugin(instance);
	}

	public static void fini() {
		ProtoPlugcan can = ProtoPlugcan.getInstance();
        can.unregisterPlugin(instance);
	}

	public VoiceCall() {
		super(VOICE_CALL);
	}

	public void input(Jabber talk, Element packet) {
		/*
		if (setDial(talk, target, "ANSWER")) {
		}
		*/
	}

	public static void dial(Jabber phone, String target) {
		if (target != null)
			instance.setDial(phone, target, "DIAL");
	}

	public static void answer(boolean rejected) {
		instance.setDialing(rejected);
	}

	public static void hangup() {
		instance.setHangup();
	}

	private void setHangup() {
	}

	private void setDialing(boolean rejected) {
	}

	String voicePeer = null; 
	String voiceMode = null;
	Jabber voicePhone = null;
	SlotSlot slotWait = new SlotSlot();
	SlotWait ringWait = new SlotWait(this);
	boolean setDial(Jabber phone, String target, String mode) {

		if (voicePeer == null) {
			synchronized(voicePeer) {
				voicePhone = phone;
				voicePeer = target;
				voiceMode = mode;
			}
			dialWait.ipcSchedule();
			return true;
		}

		return false;
	}

	public void run() {
		if (voicePeer != null && !ringWait.started()) {
			if (voiceMode.equals("DIAL")) {
				String payload = "<query xmlns='" + VOICE_CALL + "'/>";
				voicePhone.putQuery(Jabber.SET, voicePeer, payload, ringWait);
			} else if (voiceMode.equals("ANSWER")) {
				Intent intent = new Intent("wave.talk.voice.CALL_STATE_CHANGE");
				slotWait.record(ringWait);
				JabberDaemon.broadcastIntent(intent);
			}
		}
	}
}

