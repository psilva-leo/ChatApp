package uk.ac.man.cs.COMP28512.lab4;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

/**
 * Created by psilv_000 on 20/03/2016.
 */
public class RegisterDialog extends DialogFragment{
    Communicator communicator;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        communicator = (Communicator) activity;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final EditText input = new EditText(getActivity());
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Register");
        builder.setMessage("username");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                communicator.onDialogCancel();
            }
        });
        builder.setPositiveButton(R.string.signUp, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                communicator.onDialogSignUp(input.getText().toString());
            }
        });
        Dialog dialog = builder.create();

        setCancelable(false);
        return dialog;
    }

    interface Communicator{
        public void onDialogSignUp(String message);
        public void onDialogCancel();
    }
}
