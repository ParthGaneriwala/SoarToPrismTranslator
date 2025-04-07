dtmc

const integer state_ssq: [0..1] init 0;
const integer state_gender = 1;

const double state_hs = 0.2
const double state_log-hs = 0.6989
const double state_no-change = 0.6
const double state_log-no-change = 0.2218
const double state_low-to-med-prob = 0.2877
const double state_low-to-high-prob = 1.386
const double state_med-to-high-prob = 0.301
const double state_gender-factor = 1.2
const double state_l2mprob = 0.9
const double state_stay-low-prob = 0.1
const double state_log-l2mprob = 0.105
const double state_log-stay-low = 2.302
const integer state_headset_h: [1..5] init 1;


module user
    [] state_operator_name = initialize -> 0.058823529411764705: (state_name'=monitor) + 0.058823529411764705: (state_ssq'=0) + 0.058823529411764705: (state_gender'=1) + 0.058823529411764705: (state_mission-success'=none) + 0.058823529411764705: (state_opticflow_range'=low) + 0.058823529411764705: (state_hs'=0.2) + 0.058823529411764705: (state_log-hs'=0.6989) + 0.058823529411764705: (state_no-change'=0.6) + 0.058823529411764705: (state_log-no-change'=0.2218) + 0.058823529411764705: (state_low-to-med-prob'=0.2877) + 0.058823529411764705: (state_low-to-high-prob'=1.386) + 0.058823529411764705: (state_med-to-high-prob'=0.301) + 0.058823529411764705: (state_gender-factor'=1.2) + 0.058823529411764705: (state_l2mprob'=0.9) + 0.058823529411764705: (state_stay-low-prob'=0.1) + 0.058823529411764705: (state_log-l2mprob'=0.105) + 0.058823529411764705: (state_log-stay-low'=2.302);
    [] state_superstate = nil & state_name = nil -> 1.0: (state_operator_name'=initialize);
    
    [] state_gender = 1 & state_ssq = 0 & state_name = monitor & state_headset_h <= 5 & state_headset_h >= 1 -> 1.0: (state_operator_name'=ssq-stay-low);
    [] state_gender = 1 & state_ssq = 0 & state_name = monitor & state_headset_h <= 5 & state_headset_h >= 1 -> 1.0: (state_operator_name'=ssq-low-to-med);
    
    [] state_operator_name = ssq-low-to-med -> 0.5: (state_ssq'=1) + 0.5: (state_last-transition'=ssq-low-to-med);
    [] state_operator_name = ssq-stay-low -> 1.0: (state_ssq'=0);
endmodule