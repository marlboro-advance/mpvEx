mpvex = {}
function mpvex.show_text(text)
    mp.set_property("user-data/mpvex/show_text", text)
end
function mpvex.hide_ui()
    mp.set_property("user-data/mpvex/toggle_ui", "hide")
end
function mpvex.show_ui()
    mp.set_property("user-data/mpvex/toggle_ui", "show")
end
function mpvex.toggle_ui()
    mp.set_property("user-data/mpvex/toggle_ui", "toggle")
end
function mpvex.show_subtitle_settings()
   mp.set_property("user-data/mpvex/show_panel", "subtitle_settings")
end
function mpvex.show_subtitle_delay()
    mp.set_property("user-data/mpvex/show_panel", "subtitle_delay")
end
function mpvex.show_audio_delay()
    mp.set_property("user-data/mpvex/show_panel", "audio_delay")
end
function mpvex.show_video_filters()
    mp.set_property("user-data/mpvex/show_panel", "video_filters")
end
function mpvex.set_button_title(text)
   mp.set_property("user-data/mpvex/set_button_title", text)
end
function mpvex.reset_button_title(text)
    mp.set_property("user-data/mpvex/reset_button_title", "unused")
end
function mpvex.show_button()
    mp.set_property("user-data/mpvex/toggle_button", "show")
end
function mpvex.hide_button()
    mp.set_property("user-data/mpvex/toggle_button", "hide")
end
function mpvex.toggle_button()
    mp.set_property("user-data/mpvex/toggle_button", "toggle")
end
function mpvex.seek_by(value)
    mp.set_property("user-data/mpvex/seek_by", value)
end
function mpvex.seek_to(value)
    mp.set_property("user-data/mpvex/seek_to", value)
end
function mpvex.seek_by_with_text(value, text)
    mp.set_property("user-data/mpvex/seek_by_with_text", value .. "|" .. text)
end
function mpvex.seek_to_with_text(value, text)
    mp.set_property("user-data/mpvex/seek_to_with_text", value .. "|" .. text)
end
function mpvex.show_software_keyboard()
    mp.set_property("user-data/mpvex/software_keyboard", "show")
end
function mpvex.hide_software_keyboard()
    mp.set_property("user-data/mpvex/software_keyboard", "hide")
end
function mpvex.toggle_software_keyboard()
    mp.set_property("user-data/mpvex/software_keyboard", "toggle")
end

-- Set referrer for HTTP/HTTPS URLs
mp.register_event("start-file", function()
    local path = mp.get_property("path")
    if not path then return end
    if not path:match("^https?://") then return end

    -- Extract scheme + host as origin
    local origin = path:match("^(https?://[^/]+)")
    if not origin then return end

    -- Set the referrer to the origin with trailing slash
    mp.set_property("referrer", origin .. "/")
    mp.msg.info("Set referrer to: " .. origin .. "/")
end)

return mpvex
