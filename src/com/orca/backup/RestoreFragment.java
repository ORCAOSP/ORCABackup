/*
 * Copyright (C) 2012 Roman Birg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.orca.backup;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.orca.backup.restore.ICSRestore;
import com.orca.backup.restore.JBMR1Restore;
import com.orca.backup.restore.JBRestore;
import com.orca.backup.restore.Restore;
import com.orca.backup.util.Tools;
import eu.chainfire.libsuperuser.Shell;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;

public class RestoreFragment extends Fragment {

    private static final String KEY_CATS = "categories";
    private static final String KEY_CHECK_ALL = "checkAll";
    public static final String TAG = "RestoreFragment";

    String[] cats;
    CheckBox[] checkBoxes;
    CheckBox restoreAll;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            cats = getActivity().getApplicationContext().getResources()
                    .getStringArray(R.array.categories);
        else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN)
            // jellybean
            cats = getActivity().getApplicationContext().getResources()
                    .getStringArray(R.array.jbcategories);
        else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN_MR1)
            cats = getActivity().getApplicationContext().getResources()
                    .getStringArray(R.array.jbmr1_categories);

        checkBoxes = new CheckBox[cats.length];

    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!isDetached()) {
            outState.putBooleanArray(KEY_CATS, getCheckedBoxes());
            outState.putBoolean(KEY_CHECK_ALL, getShouldRestoreAll());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.restore, container, false);
        LinearLayout categories = (LinearLayout) v.findViewById(R.id.categories);
        for (int i = 0; i < cats.length; i++) {
            CheckBox b = new CheckBox(getActivity());
            b.setTag(cats[i]);
            categories.addView(b);
        }

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        restoreAll = (CheckBox) getView().findViewById(R.id.restore_all);
        restoreAll.setOnClickListener(mBackupAllListener);

        boolean[] checkStates = null;
        boolean allChecked;

        if (savedInstanceState != null) {
            checkStates = savedInstanceState.getBooleanArray(KEY_CATS);
            allChecked = savedInstanceState.getBoolean(KEY_CHECK_ALL);
        } else {
            allChecked = true;
        }

        // checkStates could have been not commited properly if it was detached
        if (savedInstanceState == null || checkStates == null) {
            checkStates = new boolean[cats.length];
            for (int i = 0; i < checkStates.length; i++) {
                checkStates[i] = true;
            }
        }

        for (int i = 0; i < checkBoxes.length; i++) {
            checkBoxes[i] = (CheckBox) getView().findViewWithTag(cats[i]);
        }
        updateState(!allChecked);
        restoreAll.setChecked(allChecked);

        for (int i = 0; i < checkBoxes.length; i++) {
            checkBoxes[i].setText(cats[i]);
            checkBoxes[i].setChecked(checkStates[i]);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.restore, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_restore:
                RestoreDialog restore = RestoreDialog.newInstance(getCheckedBoxes());
                restore.show(getFragmentManager(), "restore");
                break;
            case R.id.prefs:
                Intent p = new Intent(getActivity(), Preferences.class);
                getActivity().startActivity(p);
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    private boolean getShouldRestoreAll() {
        if (restoreAll != null)
            return restoreAll.isChecked();

        return true;
    }

    private boolean[] getCheckedBoxes() {
        boolean[] boxStates = new boolean[cats.length];
        for (int i = 0; i < cats.length; i++) {
            boxStates[i] = checkBoxes[i].isChecked();
        }
        return boxStates;
    }

    View.OnClickListener mBackupAllListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            updateState();
        }
    };

    private void updateState(boolean newState) {
        for (CheckBox b : checkBoxes) {
            b.setEnabled(newState);
        }
    }

    private void updateState() {
        CheckBox box = (CheckBox) getView().findViewById(R.id.restore_all);
        boolean newState = !box.isChecked();
        for (CheckBox b : checkBoxes) {
            b.setEnabled(newState);
        }
    }

    public static class RestoreDialog extends DialogFragment {
        static RestoreDialog newInstance() {
            return new RestoreDialog();
        }

        public static RestoreDialog newInstance(boolean[] checkedBoxes) {
            RestoreDialog r = new RestoreDialog();
            r.catsToBackup = checkedBoxes;
            return r;
        }

        boolean[] catsToBackup;

        String[] fileIds;
        File[] files;
        File backupDir;
        ArrayList<String> availableBackups = new ArrayList<String>();
        static int fileIndex = 0;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);

        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            backupDir = Tools.getBackupDirectory(getActivity());

            // This filter only returns directories
            FileFilter fileFilter = new FileFilter() {
                public boolean accept(File file) {
                    return file.isDirectory();
                }
            };
            files = backupDir.listFiles(fileFilter);
            if (files == null) {
                files = new File[0];
                fileIds = new String[0];
            } else {
                fileIds = new String[files.length];
                for (int i = 0; i < files.length; i++) {
                    fileIds[i] = files[i].getName();
                }
            }

            if (files.length > 0)
                return new AlertDialog.Builder(getActivity())
                        .setSingleChoiceItems(fileIds, 0, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                fileIndex = which;

                            }
                        })
                        .setTitle("Restore")
                        .setNegativeButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                deleteBackup(fileIds[fileIndex]);
                                dialog.dismiss();
                                Toast.makeText(getActivity(),
                                        files[fileIndex].getName() + " deleted!",
                                        Toast.LENGTH_LONG).show();
                                fileIndex = 0;
                            }
                        })
                        .setPositiveButton("Restore", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (fileIndex >= 0)
                                    restore(fileIds[fileIndex]);
                            }
                        })
                        .create();
            else
                return new AlertDialog.Builder(getActivity())
                        .setTitle("Restore")
                        .setMessage("Nothing to restore!")
                        .setNegativeButton("Ok", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create();
        }

        protected void deleteBackup(final String string) {
            new AsyncTask<Object, Object, Object>() {
                @Override
                protected Object doInBackground(Object... params) {
                    File deleteMe = new File(backupDir, string);
                    try {
                        String id = Tools.readFileToString(new File(deleteMe, "id"));
                        ParseHelpers.getInstance(getActivity()).removeId(id);
                    } catch (Exception e) {
                        // no id file, let's assume there is/was no online version
                    }
                    String command = "rm -r " + deleteMe.getAbsolutePath() + "/";
                    Shell.SU.run(command);
                    return null;
                }
            }.execute((Object) null);
        }

        private void restore(String name) {
            new RestoreTask(getActivity(), catsToBackup, name).execute();

        }

        public class RestoreTask extends AsyncTask<Void, Void, Integer> {

            AlertDialog d;
            Context context;
            Restore r;
            String name = null;
            boolean[] cats = null;
            Boolean restore;

            public RestoreTask(Context context, boolean[] cats, String name) {
                this.context = context;
                this.name = name;
                this.cats = cats;
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
                    r = new ICSRestore(context);
                else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN)
                    r = new JBRestore(context);
                else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN_MR1)
                    r = new JBMR1Restore(context);
            }

            @Override
            protected void onPreExecute() {
                if (!r.okayToRestore() && !"aokp".equals(Tools.getInstance().getProp("ro.goo.rom"))) {
                    new AlertDialog.Builder(context)
                            .setTitle("Restore failed!")
                            .setMessage("AOKP Not detected. Continue restoring?")
                            .setCancelable(false)
                            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    restore = true;
                                    d = new AlertDialog.Builder(context)
                                            .setMessage("Restore in progress")
                                            .create();
                                    d.show();
                                }
                            })
                            .setNegativeButton("No!", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    restore = false;
                                }
                            })
                            .create().show();
                } else {
                    restore = true;
                    d = new AlertDialog.Builder(context)
                            .setMessage("Restore in progress")
                            .create();
                    d.show();
                }

            }

            protected Integer doInBackground(Void... v) {
                while (restore == null) {
                    // wait until user picks an option
                }
                int result = 3;
                if (restore) {
                    Tools.mountRw();
                    synchronized (restore) {
                        result = r.restoreSettings(name, cats);
                    }
                    Tools.mountRo();
                    return result;
                } else {
                    return 3;
                }
            }

            protected void onPostExecute(Integer result) {
                if (d != null)
                    d.dismiss();
                if (result == 0) {
                    new AlertDialog.Builder(context)
                            .setTitle("Restore successful!")
                            .setMessage("You should reboot right now!")
                            .setCancelable(false)
                            .setNeutralButton("I'll reboot later", null)
                            .setPositiveButton("Reboot", new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ShellService.su(context, "reboot");
                                }
                            }).create().show();
                } else if (result == 1) {
                    new AlertDialog.Builder(context)
                            .setTitle("Restore failed!")
                            .setMessage("Try again or report this error if it keeps happening.")
                            .setCancelable(false)
                            .setNeutralButton("Ok", null)
                            .create().show();
                } else if (result == 2) {
                    new AlertDialog.Builder(context)
                            .setTitle("Restore failed!")
                            .setMessage("Your Orca version is not supported yet (or is too old)!!")
                            .setCancelable(false)
                            .setNeutralButton("Ok", null)
                            .create().show();
                } else if (result == 3) {
                    new AlertDialog.Builder(context)
                            .setTitle("Restore failed!")
                            .setMessage("Are you running An Orca Rom??")
                            .setCancelable(false)
                            .setNeutralButton("Ok", null)
                            .create().show();
                } else {
                    Toast.makeText(context.getApplicationContext(), "Restore failed!!!!",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
