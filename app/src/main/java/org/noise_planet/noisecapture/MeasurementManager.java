/*
 *  This file is part of the NoiseCapture application and OnoMap system.
 *
 *  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
 *  citizen-contributed noise data.
 *
 *  This application is co-funded by the ENERGIC-OD Project (European Network for
 *  Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 *  (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 *  PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 *  Community. The application work is also supported by the French geographic portal GEOPAL of the
 *  Pays de la Loire region (http://www.geopal.org).
 *
 *  Copyright (C) Univ Eiffel - UMRAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 *  NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 3 of
 *  the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 *  it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.You should have received a copy of the GNU General Public License along with this
 *  program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA 02110-1301  USA or see For more information,  write to Université Gustave Eiffel,
 *  14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *   or write to scientific.computing@univ-eiffel.fr
 */

package org.noise_planet.noisecapture;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.media.MicrophoneInfo;
import android.net.Uri;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * Add, remove and list all measures using android private storage.
 */
public class MeasurementManager {
    private Storage storage;
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementManager.class);

    public MeasurementManager(Context context) {
        this.storage = new Storage(context);
        // Connect to local database
    }

    /**
     * @return Record list, by time descending order. (most recent first)
     */
    public List<Storage.Record> getRecords() {
        List<Storage.Record> records = new ArrayList<>();
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            Cursor cursor = database.rawQuery("SELECT * FROM "+Storage.Record.TABLE_NAME +
                    " WHERE "+ Storage.Record.COLUMN_TIME_LENGTH + " > 0 ORDER BY " + Storage.Record
                    .COLUMN_UTC + " " +
                    "DESC",
                    null);
            try {
                while (cursor.moveToNext()) {
                    records.add(new Storage.Record(cursor));
                }
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
        return records;
    }

    /**
     * @return TrafficCalibrationSession list, by time descending order. (most recent first)
     */
    public List<Storage.TrafficCalibrationSession> getTrafficCalibrationSessions() {
        List<Storage.TrafficCalibrationSession> records = new ArrayList<>();
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            Cursor cursor = database.rawQuery("SELECT * FROM "+Storage.TrafficCalibrationSession.TABLE_NAME +
                            " ORDER BY " + Storage.TrafficCalibrationSession
                            .COLUMN_MEASUREMENT_UTC + "  DESC", null);
            try {
                while (cursor.moveToNext()) {
                    records.add(new Storage.TrafficCalibrationSession(cursor));
                }
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
        return records;
    }

    /**
     * @return Inserted calibration session index
     */
    public long addTrafficCalibrationSession(Storage.TrafficCalibrationSession trafficCalibrationSession) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            long index = database.insertOrThrow(Storage.TrafficCalibrationSession.TABLE_NAME,
                    null, trafficCalibrationSession.getContent());
            return index;
        } finally {
            database.close();
        }
    }

    /**
     * Delete all data associated with a TrafficCalibrationSession
     * @param recordId Record identifier
     */
    public void deleteTrafficCalibrationSession(int recordId) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            database.delete(Storage.TrafficCalibrationSession.TABLE_NAME, Storage.TrafficCalibrationSession.COLUMN_CALIBRATION_ID + " = ?",
                    new String[]{String.valueOf(recordId)});
        } finally {
            database.close();
        }
    }

    /**
     * Delete all data associated with a record
     * @param recordId Record identifier
     */
    public void deleteRecord(int recordId) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            database.delete(Storage.Record.TABLE_NAME, Storage.Record.COLUMN_ID + " = ?",
                    new String[]{String.valueOf(recordId)});
        } finally {
            database.close();
        }
    }

    public int deleteLastLeqs(int recordId, long fromTimestamp) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            return database.delete(Storage.Leq.TABLE_NAME,  Storage.Leq.COLUMN_RECORD_ID +
                    " = ? AND "+ Storage.Leq.COLUMN_LEQ_UTC + " > ?",
                    new String[]{String.valueOf(recordId), String.valueOf(fromTimestamp)});
        } finally {
            database.close();
        }
    }

    /**
     * Delete all data associated with a record
     * @param recordIds Record identifiers
     */
    public void deleteRecords(Collection<Integer> recordIds) {
        StringBuilder param = new StringBuilder();
        String[] paramValue = new String[recordIds.size()];
        int index = 0;
        for(int recordId : recordIds) {
            paramValue[index++] = String.valueOf(recordId);
            if(param.length() != 0) {
                param.append(",");
            }
            param.append("?");
        }
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            database.delete(Storage.Record.TABLE_NAME, Storage.Record.COLUMN_ID +
                            " IN ("+param.toString()+")", paramValue);
        } finally {
            database.close();
        }
    }

    /**
     * @return Record
     */
    public int addRecord(Storage.Record.CALIBRATION_METHODS calibrationMethod) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Storage.Record.COLUMN_UTC, System.currentTimeMillis());
            contentValues.put(Storage.Record.COLUMN_CALIBRATION_METHOD, calibrationMethod.ordinal());
            try {
                return (int) database.insertOrThrow(Storage.Record.TABLE_NAME, null, contentValues);
            } catch (SQLException sqlException) {
                LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
                return -1;
            }
        } finally {
            database.close();
        }
    }

    public void updateRecordMicrophoneDeviceSettings(int recordId, MicrophoneInfo microphoneInfo) {
        String microphoneTypeName = Storage.microphoneDeviceTypeName[0];
        String microphoneDeviceSettings = "{}";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if(microphoneInfo.getType() >= 0 && microphoneInfo.getType() < Storage.microphoneDeviceTypeName.length) {
                microphoneTypeName = Storage.microphoneDeviceTypeName[microphoneInfo.getType()];
            }
            StringBuilder sb = new StringBuilder("{");
            if(microphoneInfo.getDescription() != null) {
                sb.append(String.format(Locale.ROOT,
                        "\"description\":\"%s\",",
                        microphoneInfo.getDescription()).replace("\n", ""));
            }
            if(Float.compare(MicrophoneInfo.SENSITIVITY_UNKNOWN, microphoneInfo.getSensitivity()) != 0) {
                sb.append(String.format(Locale.ROOT,
                        "\"sensitivity\":%f,",
                        microphoneInfo.getSensitivity()));
            }
            if(Float.compare(MicrophoneInfo.SPL_UNKNOWN, microphoneInfo.getMaxSpl()) != 0) {
                sb.append(String.format(Locale.ROOT,
                        "\"max_spl\":%f,",
                        microphoneInfo.getMaxSpl()));
            }
            if(Float.compare(MicrophoneInfo.SPL_UNKNOWN, microphoneInfo.getMinSpl()) != 0) {
                sb.append(String.format(Locale.ROOT,
                        "\"min_spl\":%f,",
                        microphoneInfo.getMinSpl()));
            }
            String[] location_text = new String[] {"LOCATION_UNKNOWN","LOCATION_MAINBODY",
                    "LOCATION_MAINBODY_MOVABLE", "LOCATION_PERIPHERAL"};
            String locationValue = location_text[0];
            if(microphoneInfo.getLocation() >= 0 && microphoneInfo.getLocation() < location_text.length) {
                locationValue=location_text[microphoneInfo.getLocation()];
            }
            sb.append(String.format(Locale.ROOT,
                    "\"location\":\"%s\",",
                    locationValue));
            sb.append("}");
            microphoneDeviceSettings = sb.toString();
        }
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            try {
                database.execSQL("UPDATE " + Storage.Record.TABLE_NAME + " SET " +
                        Storage.Record.COLUMN_MICROPHONE_DEVICE_ID + " = ?," +
                        Storage.Record.COLUMN_MICROPHONE_DEVICE_SETTINGS + " = ? WHERE " +
                        Storage.Record.COLUMN_ID + " = ?",
                        new Object[]{microphoneTypeName, microphoneDeviceSettings, recordId});
            } catch (SQLException sqlException) {
                LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
            }
        } finally {
            database.close();
        }
    }

    /**
     * Fetch all leq values of a record
     * @param recordId Record identifier
     * @param frequency frequency
     * @param leqs Leq value by time and frequency in the same order of frequency list
     * @return True if recordId has been found
     */
    public boolean getRecordLeqs(int recordId, List<Integer> frequency, List<Float[]> leqs, ProgressionCallBack progressionCallBack) {
        if(progressionCallBack != null) {
            progressionCallBack.onCreateCursor(getRecord(recordId).getTimeLength());
        }
        double[] frequencies = AudioProcess.realTimeCenterFrequency;
        for(double freq : frequencies) {
            frequency.add((int)freq);
        }
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            Cursor cursor = database.rawQuery("SELECT L." + Storage.Leq.COLUMN_LEQ_ID + ", " +
                    "GROUP_CONCAT(LV." + Storage.LeqValue.COLUMN_SPL +
                    ") leq_array FROM " + Storage.Leq.TABLE_NAME + " L, " + Storage.LeqValue
                    .TABLE_NAME +
                    " LV WHERE L." + Storage.Leq.COLUMN_RECORD_ID + " = ? AND L." +
                    Storage.Leq.COLUMN_LEQ_ID + " = LV." + Storage.LeqValue.COLUMN_LEQ_ID +
                    " GROUP BY L." + Storage.Leq.COLUMN_LEQ_ID + " ORDER BY L." + Storage.Leq
                    .COLUMN_LEQ_ID + ", " +
                    Storage.LeqValue.COLUMN_FREQUENCY, new String[]{String.valueOf(recordId)});
            try {
                int leqArrayIndex = cursor.getColumnIndex("leq_array");
                boolean foundLeq = false;
                while (cursor.moveToNext()) {
                    foundLeq = true;
                    String leqStringArray = cursor.getString(leqArrayIndex);
                    StringTokenizer stringTokenizer = new StringTokenizer(leqStringArray, ",");
                    Float[] leqArray = new Float[stringTokenizer.countTokens()];
                    int i = 0;
                    while (stringTokenizer.hasMoreTokens()) {
                        try {
                            leqArray[i] = Float.valueOf(stringTokenizer.nextToken());
                        } catch (NumberFormatException ex) {
                            leqArray[i] = Float.MIN_VALUE;
                        }
                        i++;
                    }
                    leqs.add(leqArray);
                    if(progressionCallBack != null) {
                        if(!progressionCallBack.onCursorNext()) {
                            break;
                        }
                    }
                }
                return foundLeq;
            } finally {
                cursor.close();
                if(progressionCallBack != null) {
                    progressionCallBack.onDeleteCursor();
                }
            }
        } finally {
            database.close();
        }
    }

    /**
     * Fetch all leq records
     * @param recordId Record identifier, -1 for all
     * @param recordVisitor Visitor of records
     */
    @SuppressLint("Range")
    public void getRecordLocations(int recordId, RecordVisitor<LeqBatch> recordVisitor) throws IllegalStateException {
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            Cursor cursor;
            cursor = database.rawQuery("SELECT "+Storage.Leq.getAllFields("L.")+", GROUP_CONCAT(LV." + Storage.LeqValue
                    .COLUMN_SPL +
                    ") leq_array FROM " + Storage.Leq.TABLE_NAME + " L, " + Storage.LeqValue
                    .TABLE_NAME +
                    " LV WHERE L." + Storage.Leq.COLUMN_RECORD_ID + " = ? AND L." +
                    Storage.Leq.COLUMN_LEQ_ID + " = LV." + Storage.LeqValue.COLUMN_LEQ_ID +
                    " GROUP BY "+Storage.Leq.getAllFields("L.")+" ORDER BY L." +
                    Storage.Leq.COLUMN_LEQ_ID + ", " +
                    Storage.LeqValue.COLUMN_FREQUENCY, new String[]{String.valueOf(recordId)});

            try {
                recordVisitor.onCreateCursor(cursor.getCount());
                int lastId = -1;
                LeqBatch lastLeq = null;
                int leqArrayIndex = cursor.getColumnIndex("leq_array");
                while (cursor.moveToNext()) {
                    if(lastId != -1) {
                        // All frequencies for the current measurement are parsed
                        if(!recordVisitor.next(lastLeq)) {
                            lastLeq = null;
                            break;
                        }
                        lastLeq = null;
                    }
                    if(lastLeq == null) {
                        lastLeq = new LeqBatch(new Storage.Leq(cursor));
                        lastId = lastLeq.getLeq().getLeqId();
                    }
                    String leqStringArray = cursor.getString(leqArrayIndex);
                    StringTokenizer stringTokenizer = new StringTokenizer(leqStringArray, ",");
                    int i = 0;
                    while (stringTokenizer.hasMoreTokens()) {
                        try {
                            String leqValueString = stringTokenizer.nextToken();
                            if(!leqValueString.isEmpty()) {
                                Storage.LeqValue leqValue = new Storage.LeqValue(lastId,
                                        (int) AudioProcess.realTimeCenterFrequency[i++],
                                        Float.valueOf(leqValueString));
                                lastLeq.addLeqValue(leqValue);
                            }
                        } catch (NumberFormatException ex) {
                            // Could not read record value, skip
                        }
                    }
                }
                // Add last leq
                if(lastLeq != null) {
                    recordVisitor.next(lastLeq);
                }
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
    }

    public Storage.Record getRecord(int recordId) {
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            Cursor cursor = database.rawQuery("SELECT * FROM " + Storage.Record.TABLE_NAME +
                    " WHERE " + Storage.Record.COLUMN_ID + " = ?", new String[]{String.valueOf(recordId)});
            try {
                if (cursor.moveToNext()) {
                    return new Storage.Record(cursor);
                }
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
        return null;
    }

    /**
     * @param recordId Record identifier
     * @return Associated tags
     */
    public List<String> getTags(int recordId) {
        ArrayList<String> tags = new ArrayList<>();
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            Cursor cursor = database.rawQuery("SELECT * FROM " + Storage.RecordTag.TABLE_NAME +
                    " WHERE " + Storage.RecordTag.COLUMN_RECORD_ID + " = ? ORDER BY " +
                    Storage.RecordTag.COLUMN_TAG_ID, new String[]{String.valueOf(recordId)});
            try {
                int tagId = cursor.getColumnIndex(Storage.RecordTag.COLUMN_TAG_SYSTEM_NAME);
                while (cursor.moveToNext()) {
                    tags.add(cursor.getString(tagId));
                }
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
        return tags;
    }

    public void updateRecordFinal(int recordId, float leqMean, int recordTimeLength, float calibration_gain) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            try {
                database.execSQL("UPDATE " + Storage.Record.TABLE_NAME + " SET " +
                        Storage.Record.COLUMN_LEQ_MEAN + " = ?," +
                        Storage.Record.COLUMN_TIME_LENGTH + " = ?,"+
                        Storage.Record.COLUMN_CALIBRATION_GAIN + " = ? WHERE " +
                        Storage.Record.COLUMN_ID + " = ?", new Object[]{leqMean,recordTimeLength,
                        calibration_gain, recordId});
            } catch (SQLException sqlException) {
                LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
            }
        } finally {
            database.close();
        }
    }

    /**
     * Add multiple leq records
     * @param leqBatches List of Leq to add
     */
    public void addLeqBatches(List<LeqBatch> leqBatches) {
        int retry = 8;
        while(true) {
            try (SQLiteDatabase database = storage.getWritableDatabase()) {
                database.beginTransaction();
                SQLiteStatement leqStatement = database.compileStatement(
                        "INSERT INTO " + Storage.Leq.TABLE_NAME + "(" +
                                Storage.Leq.COLUMN_RECORD_ID + "," +
                                Storage.Leq.COLUMN_LEQ_UTC + "," +
                                Storage.Leq.COLUMN_LAEQ +
                                ") VALUES (?, ?, ?)");
                SQLiteStatement leqValueStatement = database.compileStatement("INSERT INTO " +
                        Storage.LeqValue.TABLE_NAME + " VALUES (?,?,?)");
                for (LeqBatch leqBatch : leqBatches) {
                    Storage.Leq leq = leqBatch.getLeq();
                    leqStatement.clearBindings();
                    leqStatement.bindLong(1, leq.getRecordId());
                    leqStatement.bindLong(2, leq.getLeqUtc());
                    leqStatement.bindDouble(3, leq.getLAeq());
                    long leqId = leqStatement.executeInsert();
                    for (Storage.LeqValue leqValue : leqBatch.getLeqValues()) {
                        leqValueStatement.clearBindings();
                        leqValueStatement.bindLong(1, leqId);
                        leqValueStatement.bindLong(2, leqValue.getFrequency());
                        leqValueStatement.bindDouble(3, leqValue.getSpl());
                        leqValueStatement.execute();
                    }
                }
                database.setTransactionSuccessful();
                database.endTransaction();
                break; // exit loop as we processed the records
            } catch (SQLiteException | IllegalStateException ex) {
                // Sql issue
                retry -= 1;
                if (retry <= 0) {
                    break;
                } else {
                    try {
                        Thread.sleep(125);
                    } catch (InterruptedException ex2) {
                        break;
                    }
                }
            }
        }
    }

    public void updateRecordUserInput(int recordId, String description, Short pleasantness,
                                      String[] tags, Uri photo_uri, String noisePartyTag) {

        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            database.beginTransaction();
            SQLiteStatement recordStatement = database.compileStatement(
                    "UPDATE " + Storage.Record.TABLE_NAME +
                            " SET "+ Storage.Record.COLUMN_DESCRIPTION + " = ?, " +
                            Storage.Record.COLUMN_PLEASANTNESS + " = ?, " +
                            Storage.Record.COLUMN_PHOTO_URI + " = ?, " +
                            Storage.Record.COLUMN_NOISEPARTY_TAG + " = ?" +
                            " WHERE " + Storage.Record.COLUMN_ID + " = ?");
            recordStatement.clearBindings();
            recordStatement.bindString(1, description);
            if(pleasantness != null) {
                recordStatement.bindLong(2, pleasantness);
            } else {
                recordStatement.bindNull(2);
            }
            recordStatement.bindString(3, photo_uri != null ? photo_uri.toString() : "");
            recordStatement.bindString(4, noisePartyTag);
            recordStatement.bindLong(5, recordId);
            recordStatement.executeUpdateDelete();
            database.delete(Storage.RecordTag.TABLE_NAME,
                    Storage.RecordTag.COLUMN_RECORD_ID + " = ?" +
                            "", new String[] {String.valueOf(recordId)});
            SQLiteStatement tagStatement = database.compileStatement(
                    "INSERT INTO " + Storage.RecordTag.TABLE_NAME +
                            "(" + Storage.RecordTag.COLUMN_RECORD_ID + ", " +
                            Storage.RecordTag.COLUMN_TAG_SYSTEM_NAME + ") " +
                            " VALUES (?, ?)");
            for(String sysTag : tags) {
                tagStatement.clearBindings();
                tagStatement.bindLong(1, recordId);
                tagStatement.bindString(2, sysTag);
                tagStatement.executeInsert();
            }
            database.setTransactionSuccessful();
            database.endTransaction();
        } finally {
            database.close();
        }
    }

    /**
     * Add a leq record
     * @param leqBatch Leq values
     */
    public void addLeqBatch(LeqBatch leqBatch) {
        addLeqBatches(Collections.singletonList(leqBatch));
    }

    public static final class LeqBatch {
        private Storage.Leq leq;
        private List<Storage.LeqValue> leqValues;

        public LeqBatch(Storage.Leq leq, List<Storage.LeqValue> leqValues) {
            this.leq = leq;
            this.leqValues = leqValues;
        }

        public LeqBatch(Storage.Leq leq) {
            this.leq = leq;
            this.leqValues = new ArrayList<>();
        }

        public void addLeqValue(Storage.LeqValue leqValue) {
            leqValues.add(leqValue);
        }

        public double computeGlobalLAeq() {
            if(leq.getLAeq() > 0) {
                return leq.getLAeq();
            } else {
                // old measurements does not store LAeq but compute third octave LAeq
                // From NoiseCapture 1.3 spectrum is not A weighted
                double energeticSum = 0;
                for (Storage.LeqValue leqValue : leqValues) {
                    energeticSum += Math.pow(10, leqValue.getSpl() / 10.);
                }
                return 10 * Math.log10(energeticSum);
            }
        }

        public Storage.Leq getLeq() {
            return leq;
        }

        public List<Storage.LeqValue> getLeqValues() {
            return leqValues;
        }
    }

    public interface RecordVisitor<El> {
        void onCreateCursor(int recordCount);
        boolean next(El record);
    }

    public interface ProgressionCallBack {
        void onCreateCursor(int recordCount);

        /**
         * Event new record (second)
         * @return False to stop iterating through records
         */
        boolean onCursorNext();
        void onDeleteCursor();
    }
}
