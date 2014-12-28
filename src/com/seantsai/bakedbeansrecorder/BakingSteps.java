package com.seantsai.bakedbeansrecorder;

public enum BakingSteps {

	TEMP_RETURNED {
		public BakingSteps next() {
			return BLOOM;
		}
	},
	BLOOM {
		public BakingSteps next() {
			return DEHYDRATE;
		}
	},
	DEHYDRATE {
		public BakingSteps next() {
			return FIRST_POP;
		}
	},
	FIRST_POP {
		public BakingSteps next() {
			return RELEASE;
		}
	},
	RELEASE {
		public BakingSteps next() {
			return END;
		}
	},
	END {
		public BakingSteps next() {
			return END;
		}
	};
	
	public abstract BakingSteps next();
	
}
