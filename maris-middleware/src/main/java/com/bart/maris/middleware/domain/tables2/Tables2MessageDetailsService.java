package com.bart.maris.middleware.domain.tables2;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.rows;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A received message's cars (old VHCCS06I: VHCCS6A, VHCCS6E; Update VHCCS6C, Delete VHCCS6D) and errors (old VHCCS07I:
 * VHCCS7A; Update VHCCS7C, Delete VHCCS7D). Both lists take one message's time stamp, or, with none chosen on VHCCS04I, the
 * time-stamp window of its last List. Messages are read from the screens' statement groups.
 */
public class Tables2MessageDetailsService {
    private final Programs programs;
    private final String carDateInvalid, carUpdated, carUpdateError, carTsRequired, carNoRequired, carOrderRequired, carConRequired,
            carDashInvalid, carDateRequired, carOrientationInvalid, carTsInvalid;
    private final String errUpdated, errUpdateError, errTsRequired, errCountRequired, errFunctionRequired, errDescRequired, errTypeRequired, errTsInvalid;

    public Tables2MessageDetailsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        this.programs = new Programs(legacy, messages);
        List<String> car = Tables2Egl.messages(screens, "VHCCS06IS_UPDATE", "MESG1", 10);
        carDateInvalid = car.get(0);
        carUpdated = car.get(1);
        carUpdateError = car.get(2);
        carTsRequired = car.get(3);
        carNoRequired = car.get(4);
        carOrderRequired = car.get(5);
        carConRequired = car.get(6);
        carDashInvalid = car.get(7);
        carDateRequired = car.get(8);
        carOrientationInvalid = car.get(9);
        carTsInvalid = Tables2Egl.messages(screens, "VHCCS06IS_DELETE", "MESG1", 1).get(0);
        List<String> err = Tables2Egl.messages(screens, "VHCCS07IS_UPDATE", "MESG1", 7);
        errUpdated = err.get(0);
        errUpdateError = err.get(1);
        errTsRequired = err.get(2);
        errCountRequired = err.get(3);
        errFunctionRequired = err.get(4);
        errDescRequired = err.get(5);
        errTypeRequired = err.get(6);
        errTsInvalid = Tables2Egl.messages(screens, "VHCCS07IS_DELETE", "MESG1", 1).get(0);
    }

    /** VHCCS06IS_INIT / VHCCS07IS_INIT: one message (its time stamp) or the List's window (from, to) */
    private static Map<String, Object> window(String timestamp, String from, String to) {
        String ts = nz(timestamp);
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("compareDtF", ts.isEmpty() ? nz(from) : ts);
        crit.put("compareDtT", ts.isEmpty() ? nz(to) : ts);
        return crit;
    }

    // ---- VHCCS06I ---------------------------------------------------------------------------------------------------

    public Tables2Messages.Cars cars(String timestamp, String from, String to) {
        ProgramResult r = programs.attempt("VHCCS6A", Map.of("VHCCS6A_CRIT", window(timestamp, from, to))).result();
        List<Tables2Messages.Car> cars = new ArrayList<>();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc == 0 || urc == 4) for (Map<String, Object> row : rows(r, "VHCCS6A_DATA", "dataRow")) {
            cars.add(new Tables2Messages.Car(text(row, "tsMsgRcvd"), text(row, "carNo"), (int) number(row, "carOrder"), text(row, "conNo"),
                    (int) number(row, "dashNo"), text(row, "dateInsrvRev"), text(row, "vagenOrientation"), text(row, "orientationDir")));
        }
        List<String> orientations = new ArrayList<>();
        Programs.Outcome o = programs.attempt("VHCCS6E", Map.of());
        if (o.ok()) for (Map<String, Object> row : rows(o.result(), "VHCCS6E_DATA", "dataRow")) orientations.add(text(row, "vagenOrientation"));
        return new Tables2Messages.Cars(cars, orientations);
    }

    /** VHCCS06IS_UPDATE: the in-service date (MM-DD-YYYY) is checked by AFCXVLD, then every field is required */
    public Tables2Messages.Saved updateCar(Tables2Messages.Car c) {
        String date = nz(c.dateInService());
        if (!date.isEmpty()) {
            date = Tables2MessagesService.ymd(date);
            String d = Tables2MessagesService.pad(date, 10);
            date = d.substring(0, 4) + "-" + d.substring(5, 7) + "-" + d.substring(8, 10);   // DT-CONV-1 with its '-' fillers
            Map<String, Object> crit = new LinkedHashMap<>();
            crit.put("dateError", "N");
            crit.put("formDate", Tables2MessagesService.ymd(date));
            crit.put("otherSqlError", "N");
            Map<String, Object> back = programs.attempt("AFCXVLD", Map.of("AFCXVLD_CRIT", crit)).result().record("AFCXVLD_CRIT");
            String err = text(back, "dateError"), sql = text(back, "otherSqlError");
            if (err.equals("Y") && sql.equals("N") || err.equals("N") && sql.equals("Y")) throw new InvalidRequestException(carDateInvalid);
        }
        String ts = nz(c.timestamp()), car = nz(c.carNo()), con = nz(c.conNo()), orientation = nz(c.orientation());
        int order = c.carOrder() == null ? 0 : c.carOrder(), dash = c.dashNo() == null ? 0 : c.dashNo();
        if (ts.isEmpty() || car.isEmpty() || order == 0 || con.isEmpty() || dash == 0 || date.isEmpty() || orientation.isEmpty()) {
            String m = "";                                                       // the last check that fails wins, as on the old screen
            if (ts.isEmpty()) m = carTsRequired;
            if (car.isEmpty()) m = carNoRequired;
            if (order == 0) m = carOrderRequired;
            if (con.isEmpty()) m = carConRequired;
            if (dash == 0) m = carDashInvalid;
            if (!date.isEmpty()) m = carDateRequired;                              // VHCCS06IS_UPDATE tests "!=" here
            if (orientation.isEmpty()) m = carOrientationInvalid;
            throw new InvalidRequestException(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tsMsgRcvd", ts);
        data.put("carNo", car);
        data.put("carOrder", order);
        data.put("conNo", con);
        data.put("dashNo", dash);
        data.put("dateInsrvRev", date);
        data.put("vagenOrientation", orientation);
        data.put("orientationDir", nz(c.orientationDirection()));
        Programs.Outcome o = programs.attempt("VHCCS6C", Map.of("VHCCS6C_CRIT", Map.of("tsMsgRcvd", ts, "carNo", car), "VHCCS6C_DATA", data));
        return new Tables2Messages.Saved(o.ok(), o.ok() ? carUpdated : carUpdateError);
    }

    /** VHCCS06IS_DELETE: VHCCS6D runs even without a time stamp; then the screen says the time stamp is invalid */
    public Tables2Messages.Saved deleteCar(String timestamp, String car) {
        Programs.Outcome o = programs.attempt("VHCCS6D", Map.of("VHCCS6D_CRIT", Map.of("tsMsgRcvd", nz(timestamp), "carNo", nz(car))));
        if (nz(timestamp).isEmpty()) throw new InvalidRequestException(carTsInvalid);
        return new Tables2Messages.Saved(o.ok(), "");
    }

    // ---- VHCCS07I ---------------------------------------------------------------------------------------------------

    public List<Tables2Messages.Error> errors(String timestamp, String from, String to) {
        ProgramResult r = programs.attempt("VHCCS7A", Map.of("VHCCS7A_CRIT", window(timestamp, from, to))).result();
        List<Tables2Messages.Error> out = new ArrayList<>();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        if (urc == 0 || urc == 4) for (Map<String, Object> row : rows(r, "VHCCS7A_DATA", "dataRow")) {
            out.add(new Tables2Messages.Error(text(row, "tsMsgRcvd"), (int) number(row, "cntErrs"), text(row, "dateLastUpd"), text(row, "fldName"),
                    text(row, "marisFunc"), text(row, "msgErrDesc"), text(row, "msgErrType"), text(row, "termLastUpd"), text(row, "timeLastUpd")));
        }
        return out;
    }

    /** VHCCS07IS_UPDATE */
    public Tables2Messages.Saved updateError(Tables2Messages.Error e) {
        String ts = nz(e.timestamp()), function = nz(e.function()), desc = nz(e.description()), type = nz(e.type());
        int count = e.count() == null ? 0 : e.count();
        if (ts.isEmpty() || count == 0 || function.isEmpty() || desc.isEmpty() || type.isEmpty()) {
            String m = "";
            if (ts.isEmpty()) m = errTsRequired;
            if (count == 0) m = errCountRequired;
            if (function.isEmpty()) m = errFunctionRequired;
            if (desc.isEmpty()) m = errDescRequired;
            if (!type.isEmpty()) m = errTypeRequired;                              // VHCCS07IS_UPDATE tests "!=" here
            throw new InvalidRequestException(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tsMsgRcvd", ts);
        data.put("cntErrs", count);
        data.put("fldName", nz(e.fieldName()));
        data.put("marisFunc", function);
        data.put("msgErrDesc", desc);
        data.put("msgErrType", type);
        Programs.Outcome o = programs.attempt("VHCCS7C", Map.of("VHCCS7C_CRIT", Map.of("tsMsgRcvd", ts), "VHCCS7C_DATA", data));
        return new Tables2Messages.Saved(o.ok(), o.ok() ? errUpdated : errUpdateError);
    }

    /** VHCCS07IS_DELETE */
    public Tables2Messages.Saved deleteError(String timestamp) {
        Programs.Outcome o = programs.attempt("VHCCS7D", Map.of("VHCCS7D_CRIT", Map.of("tsMsgRcvd", nz(timestamp))));
        if (nz(timestamp).isEmpty()) throw new InvalidRequestException(errTsInvalid);
        return new Tables2Messages.Saved(o.ok(), "");
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
