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

const double pUserNoBoxtp = 1.0;	// User true positive for not boxed person
const double pUserNoBoxfn = 1-pUserNoBoxtp;	// User false negative for not boxed person
const double pUserNoBoxfp = 0.0;	// User false positive for not boxed person
const double pUserNoBoxtn = 1-pUserNoBoxfp;	// User true negative for not boxed person

const double pUserBoxNote = 1.0;	// User probability for noticing a person with a bounding box
const double pUserNoBoxNote = 1.0;	// User probability for noticing a person without a bounding box

// Times for events to happen.
const int appear_t = 5;         // Max time by which attacker must appear
const int box_t = 2;            // Time after attacker appears to draw bounding box
const int gate_t = 3;           // Time it takes attacker to get through gate after they appear
const int note_t = 1;           // Time after person appears it takes user to notice.
const int tag_t = 1;            // Time it takes user to tag attacker after noticing bounding box
const int inspect_t = 2;	// Time it takes user to inspect a person without a bounding box

global time : clock;

module ATR_module
	atr: [0..2] init 0;

	// Determine whether the person should have a box drawn around them or not. 1 means box, 2 means no box
	[]		atr=0 & !safe & person_clock > box_t -> pATRtp:(atr'=1) + pATRfn:(atr'=2);
	[]		atr=0 & safe & person_clock > box_t -> pATRfp:(atr'=1) + pATRtn:(atr'=2);

	[box]		atr=1 -> (atr'=0);
	[nobox]		atr=2 -> (atr'=0);

endmodule

module user_module
	user: [0..4] init 0;
	prev_state: [0..4] init 0;

	// Does the user notice the person with a bounding box?
	[]		person=2 & !noticed & person_clock > (box_t+note_t) -> pUserBoxNote:(prev_state'=user)&(user'=1) + (1-pUserBoxNote):true;
	[notice]	user=1 -> (user'=prev_state);

	// Does the user tag a noticed person with a bounding box?
	[]		person=2 & noticed & !safe & person_clock > (box_t+note_t+tag_t) -> pUserBoxtp:(user'=2) + pUserBoxfn:(user'=3);
	[]		person=2 & noticed & safe & person_clock > (box_t+note_t+tag_t) -> pUserBoxfp:(user'=2) + pUserBoxtn:(user'=3);

	[tag_unsafe]	user=2 -> (user'=0);
	[tag_safe]	user=3 -> (user'=0);


endmodule

module person_module
	person: [0..10] init 0;
	safe: bool init true;
	boxed: bool init false;
	noticed: bool init false;
	tagged: bool init false;
	person_clock: clock;

	invariant
		(person=0 => time<=appear_t)
	endinvariant

	// Person appears
	[]	person=0 -> pAttack:(person'=1)&(person_clock'=0)&(safe'=false) + pSafe:(person'=1)&(person_clock'=0)&(safe'=true);

	// Person gets examined by ATR
	[box]		person=1 -> (boxed'=true)&(person'=2);
	[nobox]		person=1 -> (boxed'=false)&(person'=3);

	// Person gets noticed by user
	[notice]	true -> (noticed'=true)&(person'=4);

	// Person gets tagged by user
	[tag_unsafe]	true -> (tagged'=true)&(person'=5);
	[tag_safe]	true -> (tagged'=false)&(person'=6);

	// People tagged unsafe never get through the gate
	[]	tagged=true -> (person'=8);

	// Does an untagged person get through the gate?
	[]	person_clock > gate_t & !tagged -> pThrough:(person'=7) + (1-pThrough):(person'=8);

endmodule