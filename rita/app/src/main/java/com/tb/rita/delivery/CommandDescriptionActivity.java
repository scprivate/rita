package com.tb.rita.delivery;

import android.app.AlertDialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tb.rita.R;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import domain.Alias;
import domain.Appliance;
import domain.Command;
import domain.CommandGrammar;
import domain.custom.MyDialogBox;
import domain.dao.AppDatabase;
import domain.models.CommandDescriptionViewModel;

/**
 * Created by thales on 04/11/17.
 */

public class CommandDescriptionActivity extends AppCompatActivity {


    // Views
    public static final String CMD_ID = "ID OF THE SELECTED COMMAND";
    public static final String ALIAS_ID = "ID OF THE SELECTED ALIAS";
    private int id_cmd;
    private final int REQUEST_SPEECH_RECOG = 2;
    private final int REQUEST_BLUETOOTH_ON = 1;
    private CommandDescriptionViewModel cmdDescrModel;
    private ImageButton tapToTalk;
    private BluetoothService btService;
    private List<Command> myCmds;
    private ListView alias_list;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.command_descr_screen);
        // Initialize class properties
        Intent intent = getIntent();
        id_cmd = intent.getIntExtra(CMD_ID, -1);
        alias_list = findViewById(R.id.descr_alias_list);

        tapToTalk = findViewById(R.id.descr_tap_talk);

        tapToTalk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onTapToTalk(view);
            }
        });

        cmdDescrModel = ViewModelProviders.of(this).get(CommandDescriptionViewModel.class);
        cmdDescrModel.createDb();
        cmdDescrModel.init(id_cmd);
        subscribeData();

        ImageButton btnRun = findViewById(R.id.descr_run);
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkBluetooth(cmdDescrModel.command.getValue().getName());
            }
        });
    }

    private void subscribeData() {
        cmdDescrModel.command.observe(this, new Observer<Command>() {
            @Override
            public void onChanged(@Nullable Command command) {
                populateCmdName(command);
            }
        });

        cmdDescrModel.aliases.observe(this, new Observer<List<Alias>>() {
            @Override
            public void onChanged(@Nullable List<Alias> aliases) {
                populateAliasList(aliases);
            }
        });
    }
    // ========================= Transition Functions
    public void onNewAliasButtonPressed(View view) {
        Intent toNewAlias = new Intent(this, NewAliasActivity.class);
        toNewAlias.putExtra(CMD_ID, id_cmd);
        startActivity(toNewAlias);
    }

    public void OnBackButtonPressed(View view) {
        Intent toCmdList = new Intent(this, CommandsListActivity.class);
        startActivity(toCmdList);
    }

    public void onAliasPressed(int alias_id, View view) {
        Intent intent = new Intent(this, NewAliasActivity.class);
        intent.putExtra(CMD_ID, id_cmd);
        intent.putExtra(NewAliasActivity.IS_EDIT, true);
        intent.putExtra(ALIAS_ID, alias_id);
        startActivity(intent);
    }

    // ========================= Dataview Functions
    private void populateAliasList(List<Alias> aliases) {
        if(aliases == null)
            aliases = new ArrayList<>();
        final ArrayAdapter<Alias> adapter = new ArrayAdapter<>(this,
                R.layout.support_simple_spinner_dropdown_item, aliases);
        alias_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                onAliasPressed(adapter.getItem(i).getId_alias(), view);
            }
        });
        alias_list.setAdapter(adapter);
    }

    private void populateCmdName(Command cmd) {
        if(cmd == null)
            cmd = new Command("", 1);
        TextView cmdNameView = findViewById(R.id.descr_cmd_name);
        cmdNameView.setText(cmd.getName());
    }

    // ================================== Speech  Recognition Functions
    public void onTapToTalk(View view) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt_title));
        try {
            startActivityForResult(intent, REQUEST_SPEECH_RECOG);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.unsuportted_speech),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void checkBluetooth(String msg) {
        btService = new BluetoothService(this);
        if(!btService.isBluetoothEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, REQUEST_BLUETOOTH_ON);
        } else {
            // Bluetooth já está ativado
            onSpeechFound(msg);
        }
    }

    private void onSpeechFound(String text) {
        List<Command> cmds = new ArrayList<>();
        CommandGrammar cmdGrammar = new CommandGrammar(cmds);
        String cmd = cmdGrammar.getValidCmdFromText(text);
        btService.connectAndSend(cmd);
    }

    private void beginConnection() {
        String msg = "";
        if(cmdDescrModel != null && cmdDescrModel.command != null) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
                    myCmds = db.commandDao().getAll().getValue();
                }
            };
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            CommandGrammar gram = new CommandGrammar(myCmds);
            msg = gram.getValidCmdFromText(cmdDescrModel.command.getValue());
        }
        btService.connectAndSend(msg);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        String msg = "";
        switch (requestCode) {
            case REQUEST_BLUETOOTH_ON: {
                if(resultCode == RESULT_OK) {
                    Toast.makeText(this, "Bluetooh ativado com sucesso!", Toast.LENGTH_LONG).show();
                    beginConnection();
                } else {
                    Toast.makeText(getApplicationContext(), "Não foi possível ativar o bluetooth", Toast.LENGTH_LONG).show();
                }
                break;
            }
            case REQUEST_SPEECH_RECOG: {
                if(resultCode == RESULT_OK) {
                    ArrayList<String> mySpeech = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if(mySpeech != null) {
                        msg = mySpeech.get(0);
                        checkBluetooth(msg);
                    }
                }
                break;
            }
            default: {
                Toast.makeText(this, "ERROR", Toast.LENGTH_LONG);
            }
        }
    }
}
