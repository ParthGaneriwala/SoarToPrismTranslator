dtmc

global phase : [0..1] init 0;      // 0 = propose, 1 = apply
global state_name : [0..2] init 0;
global state_start_mission : bool init false;
global state_sick_thres : [0..2] init 0;
global state_sickness_checked : bool init false;
global state_sickness_time_interval_set : bool init false;
global state_io_sick : [0..2] init 0;
global state_io_event : [0..2] init 0;
global state_io_check_done : bool init false;
global state_sick : [0..0] init 0;
global state_time_counter : [0..1] init 1;
global state_total_time : [0..1200] init 1200;
global state_io_current_time : [0..1] init 1;
global state_io_total_time : [0..1200] init 1200;
global state_action : [0..3] init 3;
global state_operator_name : [0..4] init 0;

module user
    [] phase=0 & state_name=0 & state_sickness_time_interval_set = true & state_start_mission = true & state_time_counter < state_total_time -> 1.0 : (state_operator_name' = 1) & (phase' = 1);

// Apply transitions
endmodule

// Operator mappings: 
// 0 = initialize, 1 = SS-transition

