dtmc

// Women are more succeptible to cybersickness than men, which we model here as
// a gender_factor that is a multiple on the user state machine getting-sick transition probabilities, 
// making them more likely to transition
// to sicker states and less likely to transition to less sick states.
const double gender_factor = 1.2;

// We have chosen arbitrary base (male) probs of changing from the low to med to high states of SSQ.
const double low_to_med_prob = 0.75;
const double low_to_high_prob = 0.25;
const double med_to_high_prob = 0.50;

// Here are the gender factored probs to actually use
formula l2mprob = (gender=0 ? low_to_med_prob : low_to_med_prob*gender_factor);
formula l2hprob = (gender=0 ? low_to_high_prob : low_to_high_prob*gender_factor);
formula m2hprob = (gender=0 ? med_to_high_prob : med_to_high_prob*gender_factor);

const double hs = 0.2;	// prob that headset makes some transition to new state;
			// in below headset factor models, each state has two hs-prob transitions.
const double no_change = 1-(2*hs);	// PRISM is picky...need right hand side probs to sum to 1

// We break the headset effect 'h' into three ranges of influence over user sickness.
// (Minor detail vs prior versions: since 'labels' can only be used in properties to verify, we use formulas).

formula h = optic_flow + latency;	// each ranges 0-2

formula notsickening = (h=0);
formula sortasickening = (h>=1 & h<=2);
formula verysickening = (h>=3);

module user
	SSQ: [0..2] init 0;	// 0 is SSQ low; 1 is SSQ medium; 2 is SSQ high
	gender: [0..1] init 1;	// 0 is male; 1 is female


	// Transitions for if the user is in low SSQ state
	[]	SSQ=0 & sortasickening -> l2mprob:(SSQ'=1) + (1-l2mprob):(SSQ'=0);
	[]	SSQ=0 & verysickening  -> l2hprob:(SSQ'=2) + (1-l2hprob):(SSQ'=1);

	// Transitions for if user is in medium SSQ state
	[]	SSQ=1 & notsickening 	  -> l2mprob:(SSQ'=0) + (1-l2mprob):(SSQ'=1);
	[]	SSQ=1 & verysickening 	  -> m2hprob:(SSQ'=2) + (1-m2hprob):(SSQ'=1);

	// Transitions for if user in high SSQ state
	[]	SSQ=2 & notsickening   -> l2hprob:(SSQ'=0) + (1-l2hprob):(SSQ'=2);
	[]	SSQ=2 & sortasickening -> m2hprob:(SSQ'=1) + (1-m2hprob):(SSQ'=2);

endmodule

module optic_flow_module
  optic_flow : [0..2] init 0;	// 0=low; 1=medium; 2=high
  [] optic_flow=0 -> hs:(optic_flow'=1) + hs:(optic_flow'=2) + no_change:(optic_flow'=0);
  [] optic_flow=1 -> hs:(optic_flow'=2) + hs:(optic_flow'=0) + no_change:(optic_flow'=1);
  [] optic_flow=2 -> hs:(optic_flow'=1) + hs:(optic_flow'=0) + no_change:(optic_flow'=2);
endmodule 

module latency_module
  latency : [0..2] init 0;	// 0=low; 1=medium; 2=high
  [] latency=0 -> hs:(latency'=1) + hs:(latency'=2) + no_change:(latency'=0);
  [] latency=1 -> hs:(latency'=2) + hs:(latency'=0) + no_change:(latency'=1);
  [] latency=2 -> hs:(latency'=1) + hs:(latency'=0) + no_change:(latency'=2);
endmodule 

