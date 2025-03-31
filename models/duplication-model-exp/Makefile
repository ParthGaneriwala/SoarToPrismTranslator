# Define source and target file patterns
DSRC := $(wildcard *.dpm)
XSRC := $(wildcard *.xpm)
TARGETS := $(DSRC:.dpm=.pm) ## $(XSRC:.xpm=.pm)

# Default rule: build all .pm files
all: $(TARGETS)

# Rule to convert .dpm to .pm
%.pm: %.dpm ./dupl-prism
	@echo "Converting $< to $@"
	m4 $< | ./dupl-prism > $@

%.pm: %.xpm ./xpand
	@echo "Converting $< to $@"
	./xpand $< > $@

# Clean up generated files
clean:
	rm -f $(TARGETS)

.PHONY: all clean

