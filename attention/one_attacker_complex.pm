pta

// Event Probabilities
const double pAttack = 1.0;	// Prob person is attacker
const double pSafe = 1-pAttack;	// Prob person is safe
const double pThrough = 1.0;	// Prob a person gets through the checkpoint

const double pATRtp = 1.0;	// ATR true positive prob
const double pATRfn = 1-pATRtp;	// ATR false negative prob
const double pATRfp = 0.0;	// ATR false positive prob
const double pATRtn = 1-pATRfp;	// ATR true negative prob

const double pUserBoxtp = 1.0;	// User true positive for boxed person
const double pUserBoxfn = 1-pUserBoxtp;	// User false negative for boxed person
const double pUserBoxfp = 0.0;	// User false positive for boxed person
const double pUserBoxtn = 1-pUserBoxfp;	// User true negative for boxed person

const double pUsertp = 1.0;	// User true positive for not boxed person
const double pUserfn = 1-pUsertp;	// User false negative for not boxed person
const double pUserfp = 0.0;	// User false positive for not boxed person
const double pUsertn = 1-pUserfp;	// User true negative for not boxed person

// Times for events to happen.
const int appear_t = 5;         // Max time by which attacker must appear
const int box_t = 2;            // Time after attacker appears to draw bounding box
const int gate_t = 3;           // Time it takes attacker to get through gate after they appear
const int note_t = 1;           // Time after person appears it takes user to notice.
const int tag_t = 1;            // Time it takes user to tag attacker after noticing bounding box
const int inspect_t = 2;	// Time it takes user to inspect a person without a bounding box

global time : clock;

module attacker
	s: [0..7] init 0;
	safe : bool init true;	// Person is safe by default
	tagged : bool init false;
	ta : clock; // Time when attacker appears

	invariant
		(s=0 => time<=appear_t)
	endinvariant

	// Person appears
	[]	s=0 -> pAttack:(s'=1)&(ta'=0)&(safe'=false) + pSafe:(s'=1)&(ta'=0)&(safe'=true);

	// Bounding box drawn around Person
	// State 2 means Person is boxed; state 3 means ATR did not draw box around person
	[]	s=1 & safe=false & ta>= box_t-> pATRtp:(s'=2) + pATRfn:(s'=3);
	[]	s=1 & safe=true & ta>=box_t -> pATRfp:(s'=2) + pATRtn:(s'=3);

	// User interacts with boxed Person and marks them either unsafe (tagged) or safe (not tagged)
	// State 4 means the person has been tagged as unsafe; State 5 means tagged as safe
	[]	s=2 & safe=false & ta>=(box_t+tag_t) -> pUserBoxtp:(s'=4)&(tagged'=true) + pUserBoxfn:(s'=5)&(tagged'=false);
	[]	s=2 & safe=true  & ta>=(box_t+tag_t) -> pUserBoxfp:(s'=4)&(tagged'=true) + pUserBoxtn:(s'=5)&(tagged'=false);
	[]	s=3 & safe=false & ta>=(box_t+inspect_t) -> pUsertp:(s'=4)&(tagged'=true) + pUserfn:(s'=5)&(tagged'=false);
	[]	s=3 & safe=true  & ta>=(box_t+inspect_t) -> pUserfp:(s'=4)&(tagged'=true) + pUsertn:(s'=5)&(tagged'=false);

	// Person tagged as safe gets through the checkpoint with probability pThrough
	// State 6 is Person gets through the checkpoint, State 7 is Person does not get through
	[]	s=5 & ta>=gate_t -> pThrough:(s'=6) + (1-pThrough):(s'=7);

	// Person tagged as unsafe never gets through the checkpoint
	[]	s=4 -> (s'=7);

endmodule

module user	// Simple user model since we are packing a lot of the bookkeeping into the attacker model
	u: [0..4] init 0;

	// Person appears and is noticed by the user
	[]	u=0 & (s=2 | s=3) & ta>=note_t -> (u'=1);

	// User inspects the person. This takes longer if the person has not been boxed by the ATR
	[]	u=1 & s=4 & ta>=(note_t+tag_t) -> (u'=2);
	[]	u=1 & s=5 & ta>=(note_t+inspect_t) -> (u'=2);

	// User is done. Success is measured by how many unsafe people ended up in state 7

	// As we expand to multiple attackers, the user model will get substantially more complex as it will
	// have to include a mechanism for the user to select which person they will examine next, mechanisms
	// that probabilistically manipulate the times it takes for the users to perform actions based on how
	// overwhelmed they are, etc.
endmodule