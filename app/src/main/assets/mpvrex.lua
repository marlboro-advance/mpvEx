mpvrex = {}
function mpvrex.show_text(text)
    mp.set_property("user-data/mpvrex/show_text", text)
end
function mpvrex.hide_ui()
    mp.set_property("user-data/mpvrex/toggle_ui", "hide")
end
function mpvrex.show_ui()
    mp.set_property("user-data/mpvrex/toggle_ui", "show")
end
function mpvrex.toggle_ui()
    mp.set_property("user-data/mpvrex/toggle_ui", "toggle")
end
function mpvrex.show_subtitle_settings()
   mp.set_property("user-data/mpvrex/show_panel", "subtitle_settings")
end
function mpvrex.show_subtitle_delay()
    mp.set_property("user-data/mpvrex/show_panel", "subtitle_delay")
end
function mpvrex.show_audio_delay()
    mp.set_property("user-data/mpvrex/show_panel", "audio_delay")
end
function mpvrex.show_video_filters()
    mp.set_property("user-data/mpvrex/show_panel", "video_filters")
end
function mpvrex.set_button_title(text)
   mp.set_property("user-data/mpvrex/set_button_title", text)
end
function mpvrex.reset_button_title(text)
    mp.set_property("user-data/mpvrex/reset_button_title", "unused")
end
function mpvrex.show_button()
    mp.set_property("user-data/mpvrex/toggle_button", "show")
end
function mpvrex.hide_button()
    mp.set_property("user-data/mpvrex/toggle_button", "hide")
end
function mpvrex.toggle_button()
    mp.set_property("user-data/mpvrex/toggle_button", "toggle")
end
function mpvrex.seek_by(value)
    mp.set_property("user-data/mpvrex/seek_by", value)
end
function mpvrex.seek_to(value)
    mp.set_property("user-data/mpvrex/seek_to", value)
end
function mpvrex.seek_by_with_text(value, text)
    mp.set_property("user-data/mpvrex/seek_by_with_text", value .. "|" .. text)
end
function mpvrex.seek_to_with_text(value, text)
    mp.set_property("user-data/mpvrex/seek_to_with_text", value .. "|" .. text)
end
function mpvrex.show_software_keyboard()
    mp.set_property("user-data/mpvrex/software_keyboard", "show")
end
function mpvrex.hide_software_keyboard()
    mp.set_property("user-data/mpvrex/software_keyboard", "hide")
end
function mpvrex.toggle_software_keyboard()
    mp.set_property("user-data/mpvrex/software_keyboard", "toggle")
end
return mpvrex
