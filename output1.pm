pta

// PTA generated from Soar rules using time scale 1.000
const double time_scale = 1.000;
// time units: time_units
const double N = 20.0;
const double max_deadline = 20.0;
const int deadline;
const int use_box;
const double box_accuracy;

module user
    state_name : [0..2] init 0;
    state_start_mission : [0..1] init 0;
    state_action : [0..5] init 0;
    state_current_person_id : [0..20] init 0;
    state_current_person_boxed : [0..1] init use_box;
    state_io_action : [0..5] init 0;
    state_io_current_person_id : [0..20] init 0;
    state_io_current_person_boxed : [0..1] init use_box;
    state_io_sys_start_mission : [0..1] init 0;
    t1 : clock;

    [initialize] state_start_mission = 0 & init_phase = 0 -> 1.0 : (state_name' = 0) & (state_start_mission' = 0) & (state_action' = 0) & (state_current_person_id' = 0) & (state_current_person_boxed' = 0) & (state_io_action' = 0) & (state_io_current_person_id' = 0) & (state_io_current_person_boxed' = 0);
    [examine_box_hit] state_tagged_attacker = 0 & state_current_person_boxed = 1 & state_name = 2 & state_io_scenario_attacker_id = state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_action' = 2) & (state_current_person_id' = state_current_person_id + 1) & (state_current_person_boxed' = 0) & (state_io_action' = 1) & (state_io_current_person_id' = state_current_person_id + 1) & (state_io_current_person_boxed' = 0);
    [examine_box_miss] state_tagged_attacker = 0 & state_current_person_boxed = 1 & state_name = 2 & state_io_scenario_attacker_id != state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_action' = 1) & (state_current_person_id' = state_current_person_id + 1) & (state_current_person_boxed' = 0) & (state_io_action' = 2) & (state_io_current_person_id' = state_current_person_id + 1) & (state_io_current_person_boxed' = 0);
    [examine_hit] state_tagged_attacker = 0 & state_current_person_boxed = 0 & state_name = 2 & state_io_scenario_attacker_id = state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_action' = 2) & (state_current_person_id' = state_current_person_id + 1) & (state_io_action' = 1) & (state_io_current_person_id' = state_current_person_id + 1) & (state_io_current_person_boxed' = 0);
    [examine_miss] state_tagged_attacker = 0 & state_current_person_boxed = 0 & state_name = 2 & state_io_scenario_attacker_id != state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_action' = 1) & (state_current_person_id' = state_current_person_id + 1) & (state_io_action' = 2) & (state_io_current_person_id' = state_current_person_id + 1) & (state_io_current_person_boxed' = 0);
    [deadline_reached] state_tagged_attacker = 0 & state_name = 2 & init_phase = 1 & state_wall_clock >= deadline -> 1.0 : (state_action' = 3) & (state_io_action' = 3) & (state_io_current_person_id' = state_current_person_id) & (state_io_current_person_boxed' = state_current_person_boxed);
    [mission_complete_untagged] state_tagged_attacker = 0 & state_name = 2 & init_phase = 1 & state_current_person_id >= 20 -> 1.0 : (state_action' = 4) & (state_io_action' = 4) & (state_io_current_person_id' = state_current_person_id) & (state_io_current_person_boxed' = state_current_person_boxed);
    [mission_complete_tagged] state_tagged_attacker = 1 & state_name = 2 & init_phase = 1 -> 1.0 : (state_action' = 5) & (state_io_action' = 5) & (state_io_current_person_id' = state_current_person_id) & (state_io_current_person_boxed' = state_current_person_boxed);
    [start_mission] state_start_mission = 0 & init_phase = 1 -> 1.0 : (state_start_mission' = 1);
    [transition_mission] state_name = 0 & init_phase = 1 -> 1.0 : (state_name' = 2) & (state_action' = 1) & (state_current_person_id' = 0) & (state_current_person_boxed' = use_box) & (state_io_action' = 2) & (state_io_current_person_id' = 0) & (state_io_current_person_boxed' = use_box);
endmodule

module tagged
    state_tagged_attacker : [0..1] init 0;
    state_io_attacker_location : [0..1] init 0;
    state_io_scenario_attacker_id : [0..19] init 0;

    [initialize] state_start_mission = 0 & init_phase = 0 -> box_accuracy : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 0) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 1) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 2) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 3) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 4) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 5) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 6) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 7) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 8) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 9) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 10) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 11) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 12) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 13) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 14) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 15) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 16) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 17) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 18) + (1-box_accuracy)*0.05263157894736842 : (state_tagged_attacker' = 0) & (state_io_attacker_location' = 0) & (state_io_scenario_attacker_id' = 19);
    [examine_box_hit] state_tagged_attacker = 0 & state_current_person_boxed = 1 & state_name = 2 & state_io_scenario_attacker_id = state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_tagged_attacker' = 1) & (state_io_attacker_location' = 1);
    [examine_box_miss] state_tagged_attacker = 0 & state_current_person_boxed = 1 & state_name = 2 & state_io_scenario_attacker_id != state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_io_attacker_location' = 0);
    [examine_hit] state_tagged_attacker = 0 & state_current_person_boxed = 0 & state_name = 2 & state_io_scenario_attacker_id = state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_tagged_attacker' = 1) & (state_io_attacker_location' = 1);
    [examine_miss] state_tagged_attacker = 0 & state_current_person_boxed = 0 & state_name = 2 & state_io_scenario_attacker_id != state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_io_attacker_location' = 0);
    [deadline_reached] state_tagged_attacker = 0 & state_name = 2 & init_phase = 1 & state_wall_clock >= deadline -> 1.0 : (state_io_attacker_location' = 0);
    [mission_complete_untagged] state_tagged_attacker = 0 & state_name = 2 & init_phase = 1 & state_current_person_id >= 20 -> 1.0 : (state_io_attacker_location' = 0);
    [transition_mission] state_name = 0 & init_phase = 1 -> 1.0 : (state_tagged_attacker' = 0);
endmodule

module attacker
    state_operator_target_id : [0..1] init 0;
    state_operator_value : [0..1] init 0;
    init_clock : clock;

endmodule

module deadline_clock
    state_wall_clock : [0..20] init 0;
    wall_clock : clock;

    invariant
    (wall_clock <= deadline)
    endinvariant

    [initialize] state_start_mission = 0 & init_phase = 0 -> 1.0 : (state_wall_clock' = 0);
    [examine_box_hit] state_tagged_attacker = 0 & state_current_person_boxed = 1 & state_name = 2 & state_io_scenario_attacker_id = state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_wall_clock' = state_wall_clock + 1);
    [examine_box_miss] state_tagged_attacker = 0 & state_current_person_boxed = 1 & state_name = 2 & state_io_scenario_attacker_id != state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_wall_clock' = state_wall_clock + 1);
    [examine_hit] state_tagged_attacker = 0 & state_current_person_boxed = 0 & state_name = 2 & state_io_scenario_attacker_id = state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_wall_clock' = state_wall_clock + 1);
    [examine_miss] state_tagged_attacker = 0 & state_current_person_boxed = 0 & state_name = 2 & state_io_scenario_attacker_id != state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_wall_clock' = state_wall_clock + 1);
    [transition_mission] state_name = 0 & init_phase = 1 -> 1.0 : (state_wall_clock' = 0);
endmodule

module environment
    state_environment : [0..1] init 0;
    state_enviroSet : [0..1] init 0;
    state_crowd_person_id : [0..1] init 0;
    state_crowd_person_line : [0..1] init 0;
    state_crowd_person_index : [0..1] init 0;
    state_crowd_person_x : [0..1] init 0;
    state_crowd_person_y : [0..1] init 0;
    state_io_current_time_inspecting : [0..1] init 0;
    state_io_current_time_interacting : [0..1] init 0;
    state_io_timing_step_ready : [1..2] init 1;
    state_operator_crowd : [0..1] init 0;
    state_crowd : [0..1] init 0;

    [initialize] state_start_mission = 0 & init_phase = 0 -> 1.0 : (state_environment' = 0) & (state_enviroSet' = 0) & (state_crowd_person_id' = 0) & (state_crowd_person_line' = 0) & (state_crowd_person_index' = 0) & (state_crowd_person_x' = 0) & (state_crowd_person_y' = 0) & (state_io_current_time_inspecting' = 0) & (state_io_current_time_interacting' = 0);
    [examine_box_hit] state_tagged_attacker = 0 & state_current_person_boxed = 1 & state_name = 2 & state_io_scenario_attacker_id = state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_io_current_time_inspecting' = state_io_current_time_inspecting + 1);
    [examine_box_miss] state_tagged_attacker = 0 & state_current_person_boxed = 1 & state_name = 2 & state_io_scenario_attacker_id != state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_io_current_time_inspecting' = state_io_current_time_inspecting + 1);
    [examine_hit] state_tagged_attacker = 0 & state_current_person_boxed = 0 & state_name = 2 & state_io_scenario_attacker_id = state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_io_current_time_inspecting' = state_io_current_time_inspecting + 1);
    [examine_miss] state_tagged_attacker = 0 & state_current_person_boxed = 0 & state_name = 2 & state_io_scenario_attacker_id != state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_io_current_time_inspecting' = state_io_current_time_inspecting + 1);
    [sync_environment] state_enviroSet = 0 & init_phase = 1 -> 1.0 : (state_enviroSet' = 1) & (state_crowd' = 0);
    [transition_mission] state_name = 0 & init_phase = 1 -> 1.0 : (state_environment' = 1);
endmodule

module context
    init_phase : [0..1] init 0;
    state_atr_reviewed : [0..1] init 0;
    state_time_to_inspect : [1..2] init 1;
    state_current_time_inspecting : [0..1] init 0;
    state_current_time_interacting : [0..1] init 0;
    state_superstate : [0..1] init 0;
    state_sm : [0..1] init 0;

    [initialize] state_start_mission = 0 & init_phase = 0 -> 1.0 : (state_atr_reviewed' = 0) & (state_time_to_inspect' = 1) & (state_current_time_inspecting' = 0) & (state_current_time_interacting' = 0) & (init_phase' = 1);
    [examine_box_hit] state_tagged_attacker = 0 & state_current_person_boxed = 1 & state_name = 2 & state_io_scenario_attacker_id = state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_current_time_inspecting' = state_io_current_time_inspecting + 1);
    [examine_box_miss] state_tagged_attacker = 0 & state_current_person_boxed = 1 & state_name = 2 & state_io_scenario_attacker_id != state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_current_time_inspecting' = state_io_current_time_inspecting + 1);
    [examine_hit] state_tagged_attacker = 0 & state_current_person_boxed = 0 & state_name = 2 & state_io_scenario_attacker_id = state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_current_time_inspecting' = state_io_current_time_inspecting + 1);
    [examine_miss] state_tagged_attacker = 0 & state_current_person_boxed = 0 & state_name = 2 & state_io_scenario_attacker_id != state_current_person_id & init_phase = 1 & state_wall_clock < deadline -> 1.0 : (state_current_time_inspecting' = state_io_current_time_inspecting + 1);
    [start_mission] state_start_mission = 0 & init_phase = 1 -> 1.0 : (state_sm' = 0);
    [transition_mission] state_name = 0 & init_phase = 1 -> 1.0 : (state_atr_reviewed' = 0) & (state_current_time_inspecting' = 0) & (state_current_time_interacting' = 0);
endmodule


