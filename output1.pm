dtmc
//PRISM model generated from Soar cognitive model
//Total time: 1200

const int TOTAL_TIME = 1200;
const int sickness_monitor = 1;
const int mission_monitor  = 0;
const double pdf1 = 0.90;
const double initialAction = 3.00;


module time
  time_counter : [0..TOTAL_TIME] init 0;
  [sync] time_counter <  TOTAL_TIME -> (time_counter' = time_counter + 1);
  [sync] time_counter =  TOTAL_TIME -> (time_counter' = time_counter);
endmodule

module action_state
  action : [0..3] init 3;

  [sync] ss_transition_ing=1 & !(d_transition_ing=1) & !(dd_transition_ing=1) -> (action' = 0);
  [sync] d_transition_ing=1 & !(ss_transition_ing=1) & !(dd_transition_ing=1) -> (action' = 1);
  [sync] dd_transition_ing=1 & !(ss_transition_ing=1) & !(d_transition_ing=1) -> (action' = 2);

  [sync] !(ss_transition_ing=1) & !(d_transition_ing=1) & !(dd_transition_ing=1) -> (action' = action);
endmodule

module sickness
  name             : [0..1] init mission_monitor;
  sick             : [0..1] init 0;
  ts               : [0..1] init 0;
  sickness_checked : [0..1] init 0;

  // ---- commit at window end ----
  [sync] time_counter =  299 -> (sick' = ts);
  [sync] time_counter =  599 -> (sick' = ts);
  [sync] time_counter =  899 -> (sick' = ts);
  [sync] time_counter = 1199 -> (sick' = ts);

  // ---- sample at window start (one check per visit) ----
  [sync] time_counter =    0 & name=sickness_monitor & sickness_checked=0 & sick=0 ->
        pdf1     : (ts'=0) & (sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (sickness_checked'=1);
  [sync] time_counter =    0 & name=sickness_monitor & sickness_checked=0 & sick=1 ->
        1 : (ts'=1) & (sickness_checked'=1);

  [sync] time_counter =  300 & name=sickness_monitor & sickness_checked=0 & sick=0 ->
        pdf1     : (ts'=0) & (sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (sickness_checked'=1);
  [sync] time_counter =  300 & name=sickness_monitor & sickness_checked=0 & sick=1 ->
        1 : (ts'=1) & (sickness_checked'=1);

  [sync] time_counter =  600 & name=sickness_monitor & sickness_checked=0 & sick=0 ->
        pdf1     : (ts'=0) & (sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (sickness_checked'=1);
  [sync] time_counter =  600 & name=sickness_monitor & sickness_checked=0 & sick=1 ->
        1 : (ts'=1) & (sickness_checked'=1);

  [sync] time_counter =  900 & name=sickness_monitor & sickness_checked=0 & sick=0 ->
        pdf1     : (ts'=0) & (sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (sickness_checked'=1);
  [sync] time_counter =  900 & name=sickness_monitor & sickness_checked=0 & sick=1 ->
        1 : (ts'=1) & (sickness_checked'=1);

  [sync] time_counter = 1200 & name=sickness_monitor & sickness_checked=0 & sick=0 ->
        pdf1     : (ts'=0) & (sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (sickness_checked'=1);
  [sync] time_counter = 1200 & name=sickness_monitor & sickness_checked=0 & sick=1 ->
        1 : (ts'=1) & (sickness_checked'=1);

  [sync] name = sickness_monitor & sickness_checked = 1 &
  time_counter != 0 & time_counter != 300 & time_counter != 600 & time_counter != 900 & time_counter != 1200 &
  time_counter != 299 & time_counter != 599 & time_counter != 899 & time_counter != 1199
->
  (name' = mission_monitor) & (sickness_checked' = 0);

  [sync]
    time_counter != 299 & time_counter != 599 & time_counter != 899 & time_counter != 1199 &
    !((time_counter =    0 | time_counter =  300 | time_counter =  600 | time_counter =  900 | time_counter = 1200) &
      name = sickness_monitor & sickness_checked = 0) &
    !(name = sickness_monitor & sickness_checked = 1)
  ->
    (sick' = sick) & (ts' = ts) & (sickness_checked' = sickness_checked) & (name' = name);
endmodule

module ss_transition
  ss_transition_done : [0..1] init 0;
  ss_transition_ing  : [0..1] init 0;

  [sync] ss_transition_ing=1 ->
    (ss_transition_done' = 1) & (ss_transition_ing' = 0);

  [sync] ss_transition_done=1 & !(ss_transition_ing=1) -> (ss_transition_done' = 0);

  [sync] !(ss_transition_done=1 & !(ss_transition_ing=1)) & !(ss_transition_ing=1) ->
    (ss_transition_done' = ss_transition_done) & (ss_transition_ing' = ss_transition_ing);
endmodule

module d_transition
  d_transition_done : [0..1] init 0;
  d_transition_ing  : [0..1] init 0;

  [sync] time_counter < TOTAL_TIME & action=0 & d_transition_done=0 & d_transition_ing=0 ->
    (d_transition_ing' = 1);

  [sync] d_transition_ing=1 ->
    (d_transition_done' = 1) & (d_transition_ing' = 0);

  [sync] d_transition_done=1 & !(d_transition_ing=1) -> (d_transition_done' = 0);

  [sync] !(d_transition_done=1 & !(d_transition_ing=1)) & !(d_transition_ing=1) & !(time_counter < TOTAL_TIME & action=0 & d_transition_done=0 & d_transition_ing=0) ->
    (d_transition_done' = d_transition_done) & (d_transition_ing' = d_transition_ing);
endmodule

module dd_transition
  dd_transition_done : [0..1] init 0;
  dd_transition_ing  : [0..1] init 0;

  [sync] time_counter < TOTAL_TIME & action=1 & dd_transition_done=0 & dd_transition_ing=0 ->
    (dd_transition_ing' = 1);

  [sync] dd_transition_ing=1 ->
    (dd_transition_done' = 1) & (dd_transition_ing' = 0);

  [sync] dd_transition_done=1 & !(dd_transition_ing=1) -> (dd_transition_done' = 0);

  [sync] !(dd_transition_done=1 & !(dd_transition_ing=1)) & !(dd_transition_ing=1) & !(time_counter < TOTAL_TIME & action=1 & dd_transition_done=0 & dd_transition_ing=0) ->
    (dd_transition_done' = dd_transition_done) & (dd_transition_ing' = dd_transition_ing);
endmodule



// ---- Response Time Modeling ----
module response_time
  response_state : [0..60] init 0;  // 0 = idle, 1-60 = responding
  response_type  : [0..2] init 0;   // 0 = none, 1 = select, 2 = decide

  // ---- Scan-and-Select Response Distribution ----
  // Triggered when action transitions to selecting state
  [sync] action=3 & response_state=0 & sick=0 ->
    0.0487705755 : (response_state'=0) & (response_type'=1) +
    0.0463920065 : (response_state'=1) & (response_type'=1) +
    0.0441495371 : (response_state'=2) & (response_type'=1) +
    0.0420371593 : (response_state'=3) & (response_type'=1) +
    0.0400493588 : (response_state'=4) & (response_type'=1) +
    0.0381816588 : (response_state'=5) & (response_type'=1) +
    0.0299259976 : (response_state'=10) & (response_type'=1) +
    0.0234537975 : (response_state'=15) & (response_type'=1) +
    0.0183907855 : (response_state'=20) & (response_type'=1) +
    0.0144269492 : (response_state'=25) & (response_type'=1) +
    0.0113151761 : (response_state'=30) & (response_type'=1) +
    0.0088734153 : (response_state'=35) & (response_type'=1) +
    0.0069589239 : (response_state'=40) & (response_type'=1) +
    0.0054565773 : (response_state'=45) & (response_type'=1) +
    0.0042772680 : (response_state'=50) & (response_type'=1) +
    0.0033543736 : (response_state'=55) & (response_type'=1) +
    0.0026302679 : (response_state'=60) & (response_type'=1);

  [sync] action=3 & response_state=0 & sick=1 ->
    0.0099501663 : (response_state'=0) & (response_type'=1) +
    0.0098511604 : (response_state'=1) & (response_type'=1) +
    0.0097531451 : (response_state'=2) & (response_type'=1) +
    0.0096561065 : (response_state'=3) & (response_type'=1) +
    0.0095600302 : (response_state'=4) & (response_type'=1) +
    0.0094649018 : (response_state'=5) & (response_type'=1) +
    0.0089866171 : (response_state'=10) & (response_type'=1) +
    0.0085364893 : (response_state'=15) & (response_type'=1) +
    0.0081129854 : (response_state'=20) & (response_type'=1) +
    0.0077146136 : (response_state'=25) & (response_type'=1) +
    0.0073399215 : (response_state'=30) & (response_type'=1) +
    0.0069874955 : (response_state'=35) & (response_type'=1) +
    0.0066559594 : (response_state'=40) & (response_type'=1) +
    0.0063439706 : (response_state'=45) & (response_type'=1) +
    0.0060502176 : (response_state'=50) & (response_type'=1) +
    0.0057734186 : (response_state'=55) & (response_type'=1) +
    0.0055123223 : (response_state'=60) & (response_type'=1);

  // ---- Decision Response Distribution ----
  // Triggered when action transitions to deciding state
  [sync] action=0 & response_state=0 & sick=0 ->
    0.0198013267 : (response_state'=0) & (response_type'=2) +
    0.0194092342 : (response_state'=1) & (response_type'=2) +
    0.0190275789 : (response_state'=2) & (response_type'=2) +
    0.0186562134 : (response_state'=3) & (response_type'=2) +
    0.0182951150 : (response_state'=4) & (response_type'=2) +
    0.0179441490 : (response_state'=5) & (response_type'=2) +
    0.0153308659 : (response_state'=10) & (response_type'=2) +
    0.0131026862 : (response_state'=15) & (response_type'=2) +
    0.0111982249 : (response_state'=20) & (response_type'=2) +
    0.0095665256 : (response_state'=25) & (response_type'=2) +
    0.0081653499 : (response_state'=30) & (response_type'=2) +
    0.0069680867 : (response_state'=35) & (response_type'=2) +
    0.0059638427 : (response_state'=40) & (response_type'=2) +
    0.0050945578 : (response_state'=45) & (response_type'=2) +
    0.0043496594 : (response_state'=50) & (response_type'=2) +
    0.0037181129 : (response_state'=55) & (response_type'=2) +
    0.0031822393 : (response_state'=60) & (response_type'=2);

  [sync] action=0 & response_state=0 & sick=1 ->
    0.0099501663 : (response_state'=0) & (response_type'=2) +
    0.0098511604 : (response_state'=1) & (response_type'=2) +
    0.0097531451 : (response_state'=2) & (response_type'=2) +
    0.0096561065 : (response_state'=3) & (response_type'=2) +
    0.0095600302 : (response_state'=4) & (response_type'=2) +
    0.0094649018 : (response_state'=5) & (response_type'=2) +
    0.0089866171 : (response_state'=10) & (response_type'=2) +
    0.0085364893 : (response_state'=15) & (response_type'=2) +
    0.0081129854 : (response_state'=20) & (response_type'=2) +
    0.0077146136 : (response_state'=25) & (response_type'=2) +
    0.0073399215 : (response_state'=30) & (response_type'=2) +
    0.0069874955 : (response_state'=35) & (response_type'=2) +
    0.0066559594 : (response_state'=40) & (response_type'=2) +
    0.0063439706 : (response_state'=45) & (response_type'=2) +
    0.0060502176 : (response_state'=50) & (response_type'=2) +
    0.0057734186 : (response_state'=55) & (response_type'=2) +
    0.0055123223 : (response_state'=60) & (response_type'=2);

  // ---- Response Progress ----
  [sync] response_state > 0 -> (response_state' = response_state - 1);

  // ---- Idle State ----
  [sync] response_state = 0 & response_type > 0 ->
    (response_state' = 0) & (response_type' = 0);

  // ---- Default ----
  [sync] response_state = 0 & response_type = 0 & action != 0 & action != 3 ->
    (response_state' = response_state) & (response_type' = response_type);
endmodule


// ---- Decision Error Modeling ----
module decision_errors
  decision_correct : [0..1] init 1;  // 1 = correct, 0 = error
  error_count      : [0..10] init 0; // Track cumulative errors

  // ---- Decision Correctness Sampling ----
  // Sample when deciding (action=0) and response completes

  // Healthy agent decision correctness
  [sync] action=0 & response_state=1 & sick=0 ->
    0.9990000000 : (decision_correct'=1) +
    0.0010000000 : (decision_correct'=0) & (error_count'=min(error_count+1,10));

  // Sick agent decision correctness
  [sync] action=0 & response_state=1 & sick=1 ->
    0.9950000000 : (decision_correct'=1) +
    0.0050000000 : (decision_correct'=0) & (error_count'=min(error_count+1,10));

  // ---- Default State Maintenance ----
  [sync] !(action=0 & response_state=1) ->
    (decision_correct' = decision_correct) & (error_count' = error_count);
endmodule


// ---- Reward Structures ----
rewards "mission_completion"
  time_counter = TOTAL_TIME : 1;
endrewards

rewards "decision_quality"
  decision_correct = 1 : 1;
  decision_correct = 0 : -1;
endrewards

rewards "error_penalty"
  decision_correct = 0 : 10;
endrewards

rewards "time_cost"
  time_counter < TOTAL_TIME : 1;
endrewards

rewards "response_efficiency"
  response_state > 0 : 1;
endrewards

rewards "sickness_penalty"
  sick = 1 : 1;
endrewards

