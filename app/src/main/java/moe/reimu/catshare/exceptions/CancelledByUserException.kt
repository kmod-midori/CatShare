package moe.reimu.catshare.exceptions

import io.ktor.utils.io.CancellationException

class CancelledByUserException: CancellationException("Cancelled by user")