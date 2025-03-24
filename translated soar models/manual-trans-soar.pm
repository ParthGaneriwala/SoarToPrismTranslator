dtmc

const double low_to_med_prob = 0.75;
const double low_to_high_prob = 0.25;
const double med_to_high_prob = 0.50;
const double gender_factor = 1.2;

const double l2mprob = 0.9 //low_to_med_prob*gender-factor already done
const double stay_low_prob = 0.1 //complement already done

module user
    ssq: [0..2] init 0;
    gender: [0..1] init 1;

    [] ssq=0 & h>=1 & h<=5 & gender=1 -> l2mprob:(ssq'=1) + (stay_low_prob):(ssq'=0);

endmodule

module headset
    h: [0..8];
    optic_flow : [0..2] init 0;
    latency : [0..2] init 0;

endmodule   