dtmc
//PRISM model generated from Soar cognitive model
//Total time: 1200

const int TOTAL_TIME = 1200;
const int sickness_monitor = 1;
const int mission_monitor  = 0;
const double pdf1 = 0.90;


module time
  time_counter : [0..TOTAL_TIME] init 0;
  [sync] time_counter <  TOTAL_TIME -> (time_counter' = time_counter + 1);
  [sync] time_counter =  TOTAL_TIME -> (time_counter' = time_counter);
endmodule

module action_state
  action : [0..3] init 3;

  [sync] true -> (action' = action);
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
  time_counter != 0   & time_counter != 300 & time_counter != 600 &
  time_counter != 900 & time_counter != 1200 &
  time_counter != 299 & time_counter != 599 &
  time_counter != 899 & time_counter != 1199
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
    (ss_transition_done' = 1) & (ss_transition_ing' = 0) & (action' = 0);

  [sync] ss_transition_done=1 -> (ss_transition_done' = 0);

  [sync] !(ss_transition_done=1) & !(ss_transition_ing=1) & !(time_counter < TOTAL_TIME & true & ss_transition_done=0 & ss_transition_ing=0) ->
    (ss_transition_done' = ss_transition_done) & (ss_transition_ing' = ss_transition_ing);
endmodule

module d_transition
  d_transition_done : [0..1] init 0;
  d_transition_ing  : [0..1] init 0;

  [sync] time_counter < TOTAL_TIME & action=0 & d_transition_done=0 & d_transition_ing=0 ->
    (d_transition_ing' = 1);

  [sync] d_transition_ing=1 ->
    (d_transition_done' = 1) & (d_transition_ing' = 0) & (action' = 1);

  [sync] d_transition_done=1 -> (d_transition_done' = 0);

  [sync] !(d_transition_done=1) & !(d_transition_ing=1) & !(time_counter < TOTAL_TIME & action=0 & d_transition_done=0 & d_transition_ing=0) ->
    (d_transition_done' = d_transition_done) & (d_transition_ing' = d_transition_ing);
endmodule

module dd_transition
  dd_transition_done : [0..1] init 0;
  dd_transition_ing  : [0..1] init 0;

  [sync] time_counter < TOTAL_TIME & action=1 & dd_transition_done=0 & dd_transition_ing=0 ->
    (dd_transition_ing' = 1);

  [sync] dd_transition_ing=1 ->
    (dd_transition_done' = 1) & (dd_transition_ing' = 0) & (action' = 2);

  [sync] dd_transition_done=1 -> (dd_transition_done' = 0);

  [sync] !(dd_transition_done=1) & !(dd_transition_ing=1) & !(time_counter < TOTAL_TIME & action=1 & dd_transition_done=0 & dd_transition_ing=0) ->
    (dd_transition_done' = dd_transition_done) & (dd_transition_ing' = dd_transition_ing);
endmodule


// Reward structures can be added here
// Example:
// rewards "mission_completion"
//   time_counter = TOTAL_TIME: 1;
// endrewards

