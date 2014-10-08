package to.us.bracke.copterremote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

public class Client extends Activity {

	SharedPreferences sp;

	Socket socket;
	boolean running = false;
	BufferedReader is;
	PrintStream os;

	ToggleButton btn_connect;
	Switch btn_arm;
	Button btn_zero;
	TextView log;
	SeekBar slider_thr;

	String host;
	int port;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

		log = (TextView) findViewById(R.id.log);
		log.setMovementMethod(ScrollingMovementMethod.getInstance());

		slider_thr = (SeekBar) findViewById(R.id.slider_thr);
		int max = Integer.valueOf(sp.getString("thr_max", "0"))
				- Integer.valueOf(sp.getString("thr_min", "0"));
		slider_thr.setMax(max);
		slider_thr.setEnabled(false);
		slider_thr.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				writeSocket("thr="
						+ (progress + Integer.valueOf(sp.getString("thr_min",
								"0"))));
			}
		});

		btn_arm = (Switch) findViewById(R.id.btn_arm);
		btn_arm.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if (isChecked) {
					writeSocket("arm");
				} else {
					writeSocket("disarm");
				}

			}

		});

		btn_connect = (ToggleButton) findViewById(R.id.btn_connect);
		btn_connect.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (btn_connect.isChecked()) {
					connect();
				} else {
					disconnect();
				}

			}
		});

		btn_zero = (Button) findViewById(R.id.btn_zero);
		btn_zero.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				slider_thr.setProgress(0);
			}
		});

		writeLog("Initialized");
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				slider_thr.setProgress(slider_thr.getProgress()
						+ Integer.valueOf(sp.getString("thr_steps", "1")));
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				slider_thr.setProgress(slider_thr.getProgress()
						- Integer.valueOf(sp.getString("thr_steps", "1")));
				return true;
			}
		}
		if (event.getAction() == KeyEvent.ACTION_UP
				&& (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event
						.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)) {
			return true;
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		disconnect();
		finish();
	};

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		disconnect();
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, 0, 0, "Settings");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 0:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		}
		return false;
	}

	private void writeLog(final String str) {
		if (str != null && !str.isEmpty()) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// this will run on UI thread, so its safe to modify UI
					// views.
					log.append(str + "\n");
					final Layout layout = log.getLayout();
					if (layout != null) {
						int scrollDelta = layout.getLineBottom(log
								.getLineCount() - 1)
								- log.getScrollY()
								- log.getHeight();
						if (scrollDelta > 0)
							log.scrollBy(0, scrollDelta);
					}
				}
			});
		}
	}

	private void writeSocket(String str) {
		if (str != null && !str.isEmpty() && os != null) {
			try {
				os.println(str);
			} catch (Exception e) {
				writeLog(e.getLocalizedMessage());
			}
		}
	}

	private void connect() {
		host = sp.getString("ip", "localhost");
		port = Integer.valueOf(sp.getString("port", "8090"));
		running = true;
		new Thread(new ClientThread()).start();
	}

	private void disconnect() {
		running = false;
		if (os != null) {
			os.close();
			os = null;
		}
		if (is != null) {
			try {
				is.close();
			} catch (IOException e) {
				writeLog(e.getLocalizedMessage());
			}
			is = null;
		}
		log.setText("");
	}

	private class ClientThread implements Runnable {

		String line;

		@Override
		public void run() {
			if (host != null && !host.isEmpty()) {
				try {
					InetAddress serverAddr = InetAddress.getByName(host);
					socket = new Socket(serverAddr, port);
					os = new PrintStream(socket.getOutputStream());
					is = new BufferedReader(new InputStreamReader(
							socket.getInputStream()));
					writeLog("Connected to " + host + ":" + port);

				} catch (UnknownHostException e) {
					writeLog(e.getLocalizedMessage());
				} catch (IOException e) {
					writeLog(e.getLocalizedMessage());
				}
				while (running) {
					try {
						line = is.readLine();
					} catch (IOException e) {
						writeLog(e.getLocalizedMessage());
					}
					if (line != null && !line.isEmpty()) {
						writeLog(line);
					}
				}

			} else {
				writeLog("Please set an IP and Port in the Preferences!");
			}
		}
	}
}