dtmc

const integer state_ssq: [0..1] init 0;
const integer state_gender = 1;
const double state_hs = 0.2
const double state_log_hs = 0.6989
const double state_no_change = 0.6
const double state_log_no_change = 0.2218
const double state_low_to_med_prob = 0.2877
const double state_low_to_high_prob = 1.386
const double state_med_to_high_prob = 0.301
const double state_gender_factor = 1.2
const double state_l2mprob = 0.9
const double state_stay_low_prob = 0.1
const double state_log_l2mprob = 0.105
const double state_log_stay_low = 2.302
const integer state_headset_h: [1..5] init 1;


module user
    [] state_superstate = nil 
        & state_name = nil -> state_low_to_med_prob: (state_ssq'=1) + state_stay_low_prob: (state_ssq'=0);
    [] state_gender = 1 
        & state_ssq = 0 
        & state_name = monitor 
        & state_headset_h <= 5 
        & state_headset_h >= 1 
    -> state_low_to_med_prob: (state_ssq'=1) + state_stay_low_prob: (state_ssq'=0);
endmodule