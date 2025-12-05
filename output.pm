dtmc

const double state_sick_thres = 0.5;

module user
    state_sick: [0..1] init 0;
    state_time_counter: [0..1] init 1;
    state_total_time: [0..1200] init 1200;
    state_io_current_time: [0..1] init 1;
    state_io_total_time: [0..1200] init 1200;
    state_action: [0..3] init 3;
    [] state_sickness-time-interval-set = yes & state_sickness-checked = no & state_start-mission = yes & state_name = mission-monitor & state_io_check-sickness = yes & state_io_check-done = no -> null: ;
    [] state_sickness-checked = yes & state_name = sickness-monitor -> null: (state_sickness_checked' = no);
endmodule

