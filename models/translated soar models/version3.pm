dtmc

const double state_name = monitor;
const integer state_ssq = 0;
const integer state_gender = 1;
const double state_mission_success = none;
const integer state_opticflow_range = 0;
const double state_hs = 0.2;
const double state_log_hs = 0.6989;
const double state_no_change = 0.6;
const double state_log_no_change = 0.2218;
const double state_low_to_med_prob = 0.2877;
const double state_low_to_high_prob = 1.386;
const double state_med_to_high_prob = 0.301;
const double state_gender_factor = 1.2;
const double state_l2mprob = 0.9;
const double state_stay_low_prob = 0.1;
const double state_log_l2mprob = 0.105;
const double state_log_stay_low = 2.302;

module user
    [] state_gender = 1 & state_ssq = 0 & state_name = monitor & state_headset_h <= 5 & state_headset_h >= 1 -> state_stay_low: (state_ssq' = 0) + state_l2mprob: (state_ssq' = 1);
    [] state_name = monitor & state_opticflow_range = 0 -> state_no_change: (state_opticflow_range' = 0) + state_hs: (state_opticflow_range' = 1) + state_hs: (state_opticflow_range' = 2);
endmodule

