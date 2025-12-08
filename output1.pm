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
  state_action : [0..3] init 3;

  [sync] ss_transition_ing=1 & !(d_transition_ing=1) & !(dd_transition_ing=1) -> (state_action' = 0);
  [sync] d_transition_ing=1 & !(ss_transition_ing=1) & !(dd_transition_ing=1) -> (state_action' = 1);
  [sync] dd_transition_ing=1 & !(ss_transition_ing=1) & !(d_transition_ing=1) -> (state_action' = 2);

  [sync] !(ss_transition_ing=1) & !(d_transition_ing=1) & !(dd_transition_ing=1) -> (state_action' = state_action);
endmodule

module sickness
  state_name       : [0..1] init mission_monitor;
  state_sickness_time_interval_set : [0..1] init 0;
  ts               : [0..1] init 0;
  state_sickness_checked : [0..1] init 0;


  // ---- sample at window start (automatically in sickness monitor mode) ----
  [sync] time_counter =    0 & state_sickness_checked=0 & state_sickness_time_interval_set=0 ->
        pdf1     : (ts'=0) & (state_sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (state_sickness_checked'=1);
  [sync] time_counter =    0 & state_sickness_checked=0 & state_sickness_time_interval_set=1 ->
        1 : (ts'=1) & (state_sickness_checked'=1);

  [sync] time_counter =  300 & state_sickness_checked=0 & state_sickness_time_interval_set=0 ->
        pdf1     : (ts'=0) & (state_sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (state_sickness_checked'=1);
  [sync] time_counter =  300 & state_sickness_checked=0 & state_sickness_time_interval_set=1 ->
        1 : (ts'=1) & (state_sickness_checked'=1);

  [sync] time_counter =  600 & state_sickness_checked=0 & state_sickness_time_interval_set=0 ->
        pdf1     : (ts'=0) & (state_sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (state_sickness_checked'=1);
  [sync] time_counter =  600 & state_sickness_checked=0 & state_sickness_time_interval_set=1 ->
        1 : (ts'=1) & (state_sickness_checked'=1);

  [sync] time_counter =  900 & state_sickness_checked=0 & state_sickness_time_interval_set=0 ->
        pdf1     : (ts'=0) & (state_sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (state_sickness_checked'=1);
  [sync] time_counter =  900 & state_sickness_checked=0 & state_sickness_time_interval_set=1 ->
        1 : (ts'=1) & (state_sickness_checked'=1);

  [sync] time_counter = 1200 & state_sickness_checked=0 & state_sickness_time_interval_set=0 ->
        pdf1     : (ts'=0) & (state_sickness_checked'=1)
      + (1-pdf1) : (ts'=1) & (state_sickness_checked'=1);
  [sync] time_counter = 1200 & state_sickness_checked=0 & state_sickness_time_interval_set=1 ->
        1 : (ts'=1) & (state_sickness_checked'=1);

  // ---- commit at window end (with sickness_checked reset) ----
  // Note: Window ends double as resets for next window sampling
  [sync] time_counter =  299 & state_sickness_checked=0 -> (state_sickness_time_interval_set' = ts);
  [sync] time_counter =  299 & state_sickness_checked=1 -> (state_sickness_time_interval_set' = ts) & (state_sickness_checked' = 0);
  [sync] time_counter =  599 & state_sickness_checked=0 -> (state_sickness_time_interval_set' = ts);
  [sync] time_counter =  599 & state_sickness_checked=1 -> (state_sickness_time_interval_set' = ts) & (state_sickness_checked' = 0);
  [sync] time_counter =  899 & state_sickness_checked=0 -> (state_sickness_time_interval_set' = ts);
  [sync] time_counter =  899 & state_sickness_checked=1 -> (state_sickness_time_interval_set' = ts) & (state_sickness_checked' = 0);
  [sync] time_counter = 1199 & state_sickness_checked=0 -> (state_sickness_time_interval_set' = ts);
  [sync] time_counter = 1199 & state_sickness_checked=1 -> (state_sickness_time_interval_set' = ts) & (state_sickness_checked' = 0);

  // ---- default transition (keeps state unchanged) ----
  [sync] !(time_counter = 299) & !(time_counter = 599) & !(time_counter = 899) & !(time_counter = 1199) & !((time_counter = 0 & state_sickness_checked=0)) & !((time_counter = 300 & state_sickness_checked=0)) & !((time_counter = 600 & state_sickness_checked=0)) & !((time_counter = 900 & state_sickness_checked=0)) & !((time_counter = 1200 & state_sickness_checked=0)) ->
    (state_sickness_time_interval_set' = state_sickness_time_interval_set) & (ts' = ts) & (state_sickness_checked' = state_sickness_checked) & (state_name' = state_name);
endmodule

module ss_transition
  ss_transition_done : [0..1] init 0;
  ss_transition_ing  : [0..1] init 0;

  [sync] time_counter < TOTAL_TIME & (state_action=3 | state_action=2) & ss_transition_done=0 & ss_transition_ing=0 ->
    (ss_transition_ing' = 1);

  [sync] ss_transition_ing=1 ->
    (ss_transition_done' = 1) & (ss_transition_ing' = 0);

  [sync] ss_transition_done=1 & !(ss_transition_ing=1) -> (ss_transition_done' = 0);

  [sync] !(ss_transition_done=1 & !(ss_transition_ing=1)) & !(ss_transition_ing=1) & !(time_counter < TOTAL_TIME & (state_action=3 | state_action=2) & ss_transition_done=0 & ss_transition_ing=0) ->
    (ss_transition_done' = ss_transition_done) & (ss_transition_ing' = ss_transition_ing);
endmodule

module d_transition
  d_transition_done : [0..1] init 0;
  d_transition_ing  : [0..1] init 0;

  [sync] time_counter < TOTAL_TIME & state_action=0 & d_transition_done=0 & d_transition_ing=0 ->
    (d_transition_ing' = 1);

  [sync] d_transition_ing=1 ->
    (d_transition_done' = 1) & (d_transition_ing' = 0);

  [sync] d_transition_done=1 & !(d_transition_ing=1) -> (d_transition_done' = 0);

  [sync] !(d_transition_done=1 & !(d_transition_ing=1)) & !(d_transition_ing=1) & !(time_counter < TOTAL_TIME & state_action=0 & d_transition_done=0 & d_transition_ing=0) ->
    (d_transition_done' = d_transition_done) & (d_transition_ing' = d_transition_ing);
endmodule

module dd_transition
  dd_transition_done : [0..1] init 0;
  dd_transition_ing  : [0..1] init 0;

  [sync] time_counter < TOTAL_TIME & state_action=1 & dd_transition_done=0 & dd_transition_ing=0 ->
    (dd_transition_ing' = 1);

  [sync] dd_transition_ing=1 ->
    (dd_transition_done' = 1) & (dd_transition_ing' = 0);

  [sync] dd_transition_done=1 & !(dd_transition_ing=1) -> (dd_transition_done' = 0);

  [sync] !(dd_transition_done=1 & !(dd_transition_ing=1)) & !(dd_transition_ing=1) & !(time_counter < TOTAL_TIME & state_action=1 & dd_transition_done=0 & dd_transition_ing=0) ->
    (dd_transition_done' = dd_transition_done) & (dd_transition_ing' = dd_transition_ing);
endmodule



// ---- Response Time Modeling ----
module response_time
  response_state : [0..60] init 0;  // 0 = idle, 1-60 = responding
  response_type  : [0..2] init 0;   // 0 = none, 1 = select, 2 = decide

  // ---- Scan-and-Select Response Distribution ----
  // Triggered when state_action=3 (selecting state)
  [sync] state_action=3 & response_state=0 & state_sickness_time_interval_set=0 ->
    0.1254891290537226 : (response_state'=0) & (response_type'=1) +
    0.1193689520108686 : (response_state'=1) & (response_type'=1) +
    0.1135989662203803 : (response_state'=2) & (response_type'=1) +
    0.1081637126289054 : (response_state'=3) & (response_type'=1) +
    0.1030490025868928 : (response_state'=4) & (response_type'=1) +
    0.0982433170601717 : (response_state'=5) & (response_type'=1) +
    0.0770010880636112 : (response_state'=10) & (response_type'=1) +
    0.0603477935194048 : (response_state'=15) & (response_type'=1) +
    0.0473204104898837 : (response_state'=20) & (response_type'=1) +
    0.0371212614633398 : (response_state'=25) & (response_type'=1) +
    0.0291145139132800 : (response_state'=30) & (response_type'=1) +
    0.0228317412954684 : (response_state'=35) & (response_type'=1) +
    0.0179056591272193 : (response_state'=40) & (response_type'=1) +
    0.0140400462002630 : (response_state'=45) & (response_type'=1) +
    0.0110056244051438 : (response_state'=50) & (response_type'=1) +
    0.0086309711025747 : (response_state'=55) & (response_type'=1) +
    0.0067678108588700 : (response_state'=60) & (response_type'=1);

  [sync] state_action=3 & response_state=0 & state_sickness_time_interval_set=1 ->
    0.0730289997437676 : (response_state'=0) & (response_type'=1) +
    0.0723023490551863 : (response_state'=1) & (response_type'=1) +
    0.0715829677091855 : (response_state'=2) & (response_type'=1) +
    0.0708707555865960 : (response_state'=3) & (response_type'=1) +
    0.0701656060718059 : (response_state'=4) & (response_type'=1) +
    0.0694674133781411 : (response_state'=5) & (response_type'=1) +
    0.0659570545047791 : (response_state'=10) & (response_type'=1) +
    0.0626533529516687 : (response_state'=15) & (response_type'=1) +
    0.0595450561857939 : (response_state'=20) & (response_type'=1) +
    0.0566212165715386 : (response_state'=25) & (response_type'=1) +
    0.0538711727038209 : (response_state'=30) & (response_type'=1) +
    0.0512845506616701 : (response_state'=35) & (response_type'=1) +
    0.0488512500771622 : (response_state'=40) & (response_type'=1) +
    0.0465614154153789 : (response_state'=45) & (response_type'=1) +
    0.0444054224930732 : (response_state'=50) & (response_type'=1) +
    0.0423738630592796 : (response_state'=55) & (response_type'=1) +
    0.0404575538311526 : (response_state'=60) & (response_type'=1);

  // ---- Decision Response Distribution ----
  // Triggered when action transitions to deciding state
  [sync] state_action=0 & response_state=0 & state_sickness_time_interval_set=0 ->
    0.0991187524522147 : (response_state'=0) & (response_type'=2) +
    0.0971560696534812 : (response_state'=1) & (response_type'=2) +
    0.0952456326231711 : (response_state'=2) & (response_type'=2) +
    0.0933867020645184 : (response_state'=3) & (response_type'=2) +
    0.0915791653998049 : (response_state'=4) & (response_type'=2) +
    0.0898223482521471 : (response_state'=5) & (response_type'=2) +
    0.0767411357939338 : (response_state'=10) & (response_type'=2) +
    0.0655876207572535 : (response_state'=15) & (response_type'=2) +
    0.0560545308012298 : (response_state'=20) & (response_type'=2) +
    0.0478867954048726 : (response_state'=25) & (response_type'=2) +
    0.0408729834143182 : (response_state'=30) & (response_type'=2) +
    0.0348798880608859 : (response_state'=35) & (response_type'=2) +
    0.0298529821085006 : (response_state'=40) & (response_type'=2) +
    0.0255016352846176 : (response_state'=45) & (response_type'=2) +
    0.0217729256988988 : (response_state'=50) & (response_type'=2) +
    0.0186116170691301 : (response_state'=55) & (response_type'=2) +
    0.0159292151610216 : (response_state'=60) & (response_type'=2);

  [sync] state_action=0 & response_state=0 & state_sickness_time_interval_set=1 ->
    0.0730289997437676 : (response_state'=0) & (response_type'=2) +
    0.0723023490551863 : (response_state'=1) & (response_type'=2) +
    0.0715829677091855 : (response_state'=2) & (response_type'=2) +
    0.0708707555865960 : (response_state'=3) & (response_type'=2) +
    0.0701656060718059 : (response_state'=4) & (response_type'=2) +
    0.0694674133781411 : (response_state'=5) & (response_type'=2) +
    0.0659570545047791 : (response_state'=10) & (response_type'=2) +
    0.0626533529516687 : (response_state'=15) & (response_type'=2) +
    0.0595450561857939 : (response_state'=20) & (response_type'=2) +
    0.0566212165715386 : (response_state'=25) & (response_type'=2) +
    0.0538711727038209 : (response_state'=30) & (response_type'=2) +
    0.0512845506616701 : (response_state'=35) & (response_type'=2) +
    0.0488512500771622 : (response_state'=40) & (response_type'=2) +
    0.0465614154153789 : (response_state'=45) & (response_type'=2) +
    0.0444054224930732 : (response_state'=50) & (response_type'=2) +
    0.0423738630592796 : (response_state'=55) & (response_type'=2) +
    0.0404575538311526 : (response_state'=60) & (response_type'=2);

  // ---- Response Progress ----
  [sync] response_state > 0 & !(state_action=3 & response_state=0) & !(state_action=0 & response_state=0) -> (response_state' = response_state - 1);

  // ---- Idle State ----
  [sync] response_state = 0 & response_type > 0 & state_action != 3 & state_action != 0 ->
    (response_state' = 0) & (response_type' = 0);

  // ---- Default ----
  [sync] response_state = 0 & response_type = 0 & state_action != 0 & state_action != 3 ->
    (response_state' = response_state) & (response_type' = response_type);
endmodule


// ---- Decision Error Modeling ----
module decision_errors
  decision_correct : [0..1] init 1;  // 1 = correct, 0 = error
  error_count      : [0..10] init 0; // Track cumulative errors

  // ---- Decision Correctness Sampling ----
  // Sample when deciding (state_action=0) and response completes

  // Healthy agent decision correctness
  [sync] state_action=0 & response_state=1 & state_sickness_time_interval_set=0 ->
    0.9990000000 : (decision_correct'=1) +
    0.0010000000 : (decision_correct'=0) & (error_count'=min(error_count+1,10));

  // Sick agent decision correctness
  [sync] state_action=0 & response_state=1 & state_sickness_time_interval_set=1 ->
    0.9950000000 : (decision_correct'=1) +
    0.0050000000 : (decision_correct'=0) & (error_count'=min(error_count+1,10));

  // ---- Default State Maintenance ----
  [sync] !(state_action=0 & response_state=1) ->
    (decision_correct' = decision_correct) & (error_count' = error_count);
endmodule


// ---- Reward Structures ----
rewards "mission_completion"
  time_counter = TOTAL_TIME : 1;
endrewards

rewards "decision_quality"
  decision_correct = 1 : 1;
  decision_correct = 0 : 0;
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

