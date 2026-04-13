// Author: Dan Bryce, dbryce@sift.net, SIFT, LLC.
// Re. min/max: I think there is a race condition between the events where you examine the next person and the deadline fires.

// This model has three configuration parameters: 
// - use_box:      scenario assumes a person is boxed
// - deadline:     deadline to finish examining people
// - box_accuracy: true positive rate of ATR
//
// There are four modules:
// - user:           The user that can choose when to tag a person and be done
// - tagged:         Record if successfully tagged attacker
// - attacker:       Initializes the attacker probability distribution (implemented with transitions)
// - deadline_clock: The wall clock used that tracks the deadline

pta

// experiment parameters
const int deadline;        // Time bound
const int use_box;         // 0: no boxed person, 1: there is a boxed person
const double box_accuracy; // Probability that box is a true positive label for the attacker

// Other configuration
const int boxed_examine_time = 1;   // minimum time to examine person that is boxed
const int unboxed_examine_time = 1; // minimum time to examine person that is not boxed
const int N = 20;                   // num people to examine, also need to adjust attacker transitions below

// Model of user choosing to examine people
module user
    t1 : clock;                                 // reaction time
    current_person_id: [0..N] init 0;           // id of current person
    current_person_boxed: [0..2] init use_box;  // is current person boxed

    // user must examine the current person immediately
    invariant
        (current_person_id < N & current_person_boxed=0 & tagged_attacker=0 & satisfy_deadline=0 => t1 <= unboxed_examine_time) &
        (current_person_id < N & current_person_boxed=1 & tagged_attacker=0 & satisfy_deadline=0 => t1 <= boxed_examine_time)
    endinvariant

    // allow examine_box transitions when current_person_boxed
    [examine_box] current_person_boxed=1 & current_person_id=0 & t1 >= boxed_examine_time -> (t1' = 0) & (current_person_id'=current_person_id+1) & (current_person_boxed'=0);
    // allow unboxed tagging when not current_person_boxed
    [examine] current_person_boxed=0 & t1 >= unboxed_examine_time -> (t1' = 0) & (current_person_id'=current_person_id+1);

    // disallow unboxed tagging when people boxed, needed due to `examine` synchronization
    [examine] current_person_boxed=1 & t1 > deadline -> (t1' = 0) & (current_person_id'=current_person_id+1);
    // disallow examine_box transitions when not current_person_boxed, needed due to `examine_box` synchronization
    [examine_box] current_person_boxed=0 & t1 > deadline -> (t1' = 0) & (current_person_id'=current_person_id+1);
endmodule

module tagged
    tagged_attacker: [0..2] init 0; // 0: false, 1: true
    
    // person examined is the attacker
    [examine] selected=2 & tagged_attacker=0 & current_person_id=attacker_id -> (tagged_attacker'=1);
    // person examined is not the attacker
    [examine] selected=2 & tagged_attacker=0 & current_person_id!=attacker_id -> true;
    // boxed person examined is the attacker
    [examine_box] selected=2 & tagged_attacker=0 & current_person_id=attacker_id -> (tagged_attacker'=1);
    // boxed person examined is not the attacker
    [examine_box] selected=2 & tagged_attacker=0 & current_person_id!=attacker_id -> true;
endmodule

module attacker
    attacker_id: [0..N] init 0; // id of the attacker
    selected: [0..3] init 0;    // 0: not selected, 1: need second round, 2: done
    init_clock: clock;          // time taken to initialize

    // force init_0 and init_1 as urgent transitions 
    invariant
        (selected<2 => init_clock = 0)
    endinvariant

    // if boxed, set attacker_id=0 with probability box_accuracy
    [] (selected=0) & (current_person_boxed=1) -> box_accuracy: (attacker_id'=0) & (selected'=2) & (init_clock'=0) + 
                                                  (1-box_accuracy) : (selected'=1) & (init_clock'=0);
    // if not boxed, bypass setting attacker_id=0 with probability box_accuracy
    [] (selected=0) & (current_person_boxed=0) -> (selected'=1) & (init_clock'=0);

    // pick attacker_id uniformly among 1 to N-1.
    [] (selected=1) & (current_person_boxed=1) -> (1.0/(N-1)): (attacker_id'=1) & (selected'=2) & (init_clock'=0) + 
                                          (1.0/(N-1)): (attacker_id'=2) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=3) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=4) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=5) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=6) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=7) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=8) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=9) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=10) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=11) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=12) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=13) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=14) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=15) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=16) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=17) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=18) & (selected'=2)  & (init_clock'=0)+
                                          (1.0/(N-1)): (attacker_id'=19) & (selected'=2) & (init_clock'=0);

    // pick attacker_id uniformly among 0 to N-1.
    [] (selected=1) &  (current_person_boxed=0)-> (1.0/N): (attacker_id'=0) & (selected'=2)  & (init_clock'=0) + 
                                                  (1.0/N): (attacker_id'=1) & (selected'=2) & (init_clock'=0) + 
                                                  (1.0/N): (attacker_id'=2) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=3) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=4) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=5) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=6) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=7) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=8) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=9) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=10) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=11) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=12) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=13) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=14) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=15) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=16) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=17) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=18) & (selected'=2) & (init_clock'=0) +
                                                  (1.0/N): (attacker_id'=19) & (selected'=2) & (init_clock'=0);
endmodule

module deadline_clock
	wall_clock : clock; 
    satisfy_deadline: [0..2] init 0; // 0: active, 1: satisfied deadline, 2: exceeded deadline 
    
    // must finish by deadline
    invariant
        (satisfy_deadline=0 => wall_clock <= deadline) 
    endinvariant

    // allow examination before deadline
    [examine] wall_clock <= deadline & satisfy_deadline=0 -> true;
    [examine_box] wall_clock <= deadline & satisfy_deadline=0 -> true;

    // record deadline reached
    [] (satisfy_deadline=0) & wall_clock=deadline -> (satisfy_deadline'=1);
endmodule
